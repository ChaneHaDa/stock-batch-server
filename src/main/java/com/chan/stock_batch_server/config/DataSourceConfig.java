package com.chan.stock_batch_server.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@EnableJpaRepositories(
	basePackages = "com.chan.stock_batch_server.repository",
	entityManagerFactoryRef = "primaryEntityManagerFactory",
	transactionManagerRef = "primaryTransactionManager"
)
public class DataSourceConfig {

	@Primary
	@Bean(name = "primaryDataSource")
	@ConfigurationProperties(prefix = "spring.datasource.primary")
	public DataSource primaryDataSource() {
		return DataSourceBuilder.create()
			.url("jdbc:postgresql://localhost:5432/test1")
			.username("postgres")
			.password("1234")
			.driverClassName("org.postgresql.Driver")
			.build();
	}

	@Bean(name = "batchDataSource")
	public DataSource batchDataSource() {
		return DataSourceBuilder.create()
			.url("jdbc:h2:mem:batchdb;DB_CLOSE_DELAY=-1;MODE=MYSQL")
			.username("sa")
			.password("")
			.driverClassName("org.h2.Driver")
			.build();
	}

	@Primary
	@Bean(name = "primaryEntityManagerFactory")
	public LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory(
		EntityManagerFactoryBuilder builder) {
		return builder
			.dataSource(primaryDataSource())
			.packages("com.chan.stock_batch_server.model")
			.persistenceUnit("primary")
			.build();
	}

	@Primary
	@Bean(name = "primaryTransactionManager")
	public PlatformTransactionManager primaryTransactionManager(
		@Qualifier("primaryEntityManagerFactory") LocalContainerEntityManagerFactoryBean primaryEntityManagerFactory) {
		return new JpaTransactionManager(primaryEntityManagerFactory.getObject());
	}

	@Bean(name = "batchTransactionManager")
	public DataSourceTransactionManager batchTransactionManager(
		@Qualifier("batchDataSource") DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
