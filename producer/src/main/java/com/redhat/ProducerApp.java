package com.redhat;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthorizationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.strategy.RecordIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy;

import javax.security.sasl.AuthenticationException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.redhat.kafka.schema.avro.SimpleMessage;

@QuarkusMain
public class ProducerApp implements QuarkusApplication {

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

	public int run(String... args) throws Exception {

		System.out.println(keycloak_host);
		System.out.println(bootstrap_url);

		Properties p = new Properties();
		p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap_url);
		p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
		p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());
		p.setProperty(ProducerConfig.ACKS_CONFIG, "all");
		p.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + topic);

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


		org.apache.kafka.clients.producer.Producer<Object, Object> producer = new KafkaProducer<>(p);

		for (int i = 0; ; i++) {
			String str = String.format("Message# %d", i);

			SimpleMessage message = new SimpleMessage();
        	message.setContent(str);
			message.setTimestamp(System.currentTimeMillis());

			try {
				producer.send(new ProducerRecord<Object, Object>(topic, message))
						.get();

				System.out.println("Produced " + str);

			} catch (InterruptedException e) {
				throw new RuntimeException("Interrupted while sending!");

			} catch (ExecutionException e) {
				if (e.getCause() instanceof AuthenticationException
						|| e.getCause() instanceof AuthorizationException) {
					producer.close();
					producer = new KafkaProducer<>(p);
					System.out.println("AuthenticationException or AuthorizationException encountered. Restarting producer.");
				} else {
					throw new RuntimeException("Failed to send message: " + i, e);
				}
			}

			try {
				Thread.sleep(15000);
			} catch (InterruptedException e) {
				throw new RuntimeException("Interrupted while sleeping!");
			}
		}

	}
}
