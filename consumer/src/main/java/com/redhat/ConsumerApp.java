package com.redhat;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.strategy.RecordIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;


@QuarkusMain
public class ConsumerApp implements QuarkusApplication {

	@ConfigProperty(name = "keycloak.host")
	String keycloak_host;

	@ConfigProperty(name = "keycloak.realm")
	String realm;

	@ConfigProperty(name = "keycloak.client.id")
	String clientId;

	@ConfigProperty(name = "keycloak.client.secret")
	String secret;

	@ConfigProperty(name = "kafka.bootstrap.url")
	String bootstrap_url;
	
	@ConfigProperty(name = "kafka.topic")
	String topic;

	@ConfigProperty(name = "ssl.truststore.path")
	String truststore_path;
	
	@ConfigProperty(name = "ssl.truststore.password")
	public String truststore_password;

	@ConfigProperty(name = "registry.url")
	String registry_url;
	@Override
	public int run(String... args) throws Exception {
		System.out.println(keycloak_host);
		System.out.println(bootstrap_url);

		Properties p = new Properties();
		p.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_url);
		p.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
		p.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());
		p.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + topic);

		p.setProperty("security.protocol", "SASL_SSL");
		p.setProperty("sasl.mechanism", "OAUTHBEARER");
		p.setProperty("sasl.jaas.config", String.format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
			"  oauth.client.id=\"%s\" "+
			"  oauth.client.secret=\"%s\" "+
			"  oauth.token.endpoint.uri=\"%s\" ;", clientId, secret, keycloak_host+"/auth/realms/"+realm+"/protocol/openid-connect/token"));
		p.setProperty("sasl.login.callback.handler.class", "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");

		p.setProperty("ssl.truststore.location", truststore_path);
		p.setProperty("ssl.truststore.type", "jks");
		p.setProperty("ssl.truststore.password", truststore_password);

		p.setProperty(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, registry_url);
		p.setProperty(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, RecordIdStrategy.class.getName());
		p.setProperty(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM, GetOrCreateIdStrategy.class.getName());

		p.setProperty("apicurio.registry.url", registry_url);

		KafkaConsumer<Object, Object> consumer = new KafkaConsumer<>(p);

		consumer.subscribe(Arrays.asList(topic));
		while (true) {
			ConsumerRecords<Object, Object> records = consumer.poll(Duration.ofMillis(100));
			for (ConsumerRecord<Object, Object> record : records) {
				System.out.println("Key: " + record.key() + ", Value:" + record.value());
			}
		}
	}
}
