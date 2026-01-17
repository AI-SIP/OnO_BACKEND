package com.aisip.OnO.backend.util.quartz;

import org.quartz.spi.JobFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfig {

    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(JobFactory jobFactory, DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);
        factory.setDataSource(dataSource);
        factory.setOverwriteExistingJobs(true);
        factory.setStartupDelay(5);
        factory.setAutoStartup(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);

        // Quartz 스케줄러 시간대를 한국 시간으로 설정
        Properties quartzProperties = new Properties();
        quartzProperties.setProperty("org.quartz.scheduler.timeZone", "Asia/Seoul");
        factory.setQuartzProperties(quartzProperties);

        return factory;
    }
}
