package com.chan.stock_batch_server.config;

import javax.sql.DataSource;

import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.scope.StepScope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

	@Bean
	@Primary
	public JobRepository jobRepository(@Qualifier("batchDataSource") DataSource dataSource,
		@Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager)
		throws Exception {
		JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
		factory.setDataSource(dataSource);
		factory.setTransactionManager(transactionManager);
		factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
		factory.setTablePrefix("BATCH_");
		factory.setDatabaseType("H2");
		factory.afterPropertiesSet();
		return factory.getObject();
	}

	@Bean
	@Primary
	public JobExplorer jobExplorer(@Qualifier("batchDataSource") DataSource dataSource,
		@Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager) throws Exception {
		JobExplorerFactoryBean factory = new JobExplorerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setTransactionManager(transactionManager);
		factory.afterPropertiesSet();
		return factory.getObject();
	}

	@Bean
	@Primary
	public JobLauncher jobLauncher(@Qualifier("jobRepository") JobRepository jobRepository,
		@Qualifier("batchTransactionManager") PlatformTransactionManager transactionManager)
		throws Exception {
		TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
		jobLauncher.setJobRepository(jobRepository);
		jobLauncher.setTaskExecutor(new org.springframework.core.task.SyncTaskExecutor());
		jobLauncher.afterPropertiesSet();
		return jobLauncher;
	}

	@Bean
	public StepScope stepScope() {
		return new StepScope();
	}

	@Bean
	public DataSourceInitializer batchDataSourceInitializer(@Qualifier("batchDataSource") DataSource dataSource) {
		DataSourceInitializer initializer = new DataSourceInitializer();
		initializer.setDataSource(dataSource);
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
		populator.addScript(new ClassPathResource("org/springframework/batch/core/schema-drop-h2.sql"));
		populator.addScript(new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
		initializer.setDatabasePopulator(populator);
		initializer.setEnabled(true);
		return initializer;
	}
}
