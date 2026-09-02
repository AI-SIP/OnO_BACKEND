package com.aisip.OnO.backend.util.quartz;

import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.quartz.JobStoreType;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
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

    /**
     * 스케줄러를 직접 구성한다. Spring Boot 의 자동 구성은 이 빈이 있으면 물러난다.
     *
     * <p>이전에는 DataSource 와 autoStartup 을 코드에 박아 두어 {@code spring.quartz.*} 설정이
     * 통째로 무시됐다. 그 결과 테스트 프로필이 {@code job-store-type: memory} 를 선언해도
     * JDBC 잡스토어가 강제로 붙어, QRTZ_ 테이블이 없는 테스트 DB 에서 스케줄 등록이 실패했다.
     * 스케줄러가 관여하는 경로는 목으로 대체할 수밖에 없었고 실제 등록 로직은 검증 대상 밖이었다.
     *
     * <p>이제 프로필 설정을 그대로 따른다. dev/prod/local 은 모두
     * {@code job-store-type: jdbc}, {@code auto-startup: true} 를 명시하고 있어 동작이 바뀌지 않는다.
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(JobFactory jobFactory,
                                                     ObjectProvider<DataSource> dataSourceProvider,
                                                     QuartzProperties quartzProperties) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobFactory(jobFactory);

        // 메모리 잡스토어에 DataSource 를 물리면 Quartz 가 QRTZ_ 테이블을 찾는다.
        if (quartzProperties.getJobStoreType() == JobStoreType.JDBC) {
            factory.setDataSource(dataSourceProvider.getObject());
        }

        factory.setAutoStartup(quartzProperties.isAutoStartup());

        // 아래 셋은 프로필 어디에도 설정돼 있지 않아, Spring Boot 기본값을 따르면 운영 동작이 바뀐다.
        // 특히 overwriteExistingJobs 가 false 가 되면 cron 표현식을 고쳐도 재배포 시 반영되지 않는다.
        // 테스트를 막던 것은 jobStoreType 과 autoStartup 뿐이므로 나머지는 기존 값을 유지한다.
        factory.setOverwriteExistingJobs(true);
        factory.setStartupDelay(5);
        factory.setWaitForJobsToCompleteOnShutdown(true);

        Properties properties = new Properties();
        // 스케줄 표현식(cron)을 한국 시간으로 해석한다.
        properties.setProperty("org.quartz.scheduler.timeZone", "Asia/Seoul");
        properties.putAll(quartzProperties.getProperties());
        factory.setQuartzProperties(properties);

        return factory;
    }
}
