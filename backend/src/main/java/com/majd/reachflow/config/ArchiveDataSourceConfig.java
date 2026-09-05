package com.majd.reachflow.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import java.sql.Connection;
import java.sql.SQLException;

@Configuration
public class ArchiveDataSourceConfig {

        @Bean
    @Primary
        @ConfigurationProperties(prefix = "spring.datasource")
        public DataSourceProperties primaryDataSourceProperties() {
                return new DataSourceProperties();
    }

        @Bean(name = "dataSource")
        @Primary
        public DataSource dataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties) {
                return primaryDataSourceProperties.initializeDataSourceBuilder().build();
        }

        @Bean
        @ConfigurationProperties(prefix = "spring.datasource.archive")
        public DataSourceProperties archiveDataSourceProperties() {
                return new DataSourceProperties();
        }

        @Bean(name = "archiveDataSource")
        public DataSource archiveDataSource(@Qualifier("archiveDataSourceProperties") DataSourceProperties archiveDataSourceProperties) {
                return archiveDataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("dataSource") DataSource dataSource,
            JpaProperties jpaProperties
    ) {
        return builder
                .dataSource(dataSource)
                .packages("com.majd.reachflow.entity")
                .persistenceUnit("primary")
                .properties(jpaProperties.getProperties())
                .build();
    }

    @Bean(name = "archiveEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean archiveEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("archiveDataSource") DataSource archiveDataSource,
            JpaProperties jpaProperties
    ) {
        Map<String, Object> props = new HashMap<>(jpaProperties.getProperties());
        props.put("hibernate.hbm2ddl.auto", "update");
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        return builder
                .dataSource(archiveDataSource)
                .packages("com.majd.reachflow.archive.entity")
                .persistenceUnit("archive")
                .properties(props)
                .build();
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean(name = "archiveTransactionManager")
    public PlatformTransactionManager archiveTransactionManager(
            @Qualifier("archiveEntityManagerFactory") EntityManagerFactory archiveEntityManagerFactory
    ) {
        return new JpaTransactionManager(archiveEntityManagerFactory);
    }

        @Bean
        public ApplicationRunner archiveDatabaseInitializer(@Qualifier("archiveDataSource") DataSource archiveDataSource) {
                return args -> {
                        try (Connection connection = archiveDataSource.getConnection()) {
                                connection.getMetaData();
                        } catch (SQLException ex) {
                                throw new IllegalStateException("Failed to initialize archive database", ex);
                        }
                };
        }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.majd.reachflow.repository",
            entityManagerFactoryRef = "entityManagerFactory",
            transactionManagerRef = "transactionManager"
    )
    static class PrimaryJpaRepositoriesConfig {
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = "com.majd.reachflow.archive.repository",
            entityManagerFactoryRef = "archiveEntityManagerFactory",
            transactionManagerRef = "archiveTransactionManager"
    )
    static class ArchiveJpaRepositoriesConfig {
    }
}
