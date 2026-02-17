package it.govpay.maggioli.batch.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.persistence.EntityManagerFactory;

/**
 * Workaround per incompatibilita' Hibernate 6.6.x + JPA 3.2:
 * il metodo getSchemaManager() ha return type diversi in SessionFactory e EntityManagerFactory,
 * causando un conflitto nella creazione del JDK Proxy.
 * Si imposta entityManagerFactoryInterface a EntityManagerFactory per evitare il proxy con SessionFactory.
 */
@Configuration
public class JpaConfiguration {

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryInterfacePostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                BeanDefinition bd = beanFactory.getBeanDefinition("entityManagerFactory");
                bd.getPropertyValues().add("entityManagerFactoryInterface", EntityManagerFactory.class);
            }
        };
    }
}
