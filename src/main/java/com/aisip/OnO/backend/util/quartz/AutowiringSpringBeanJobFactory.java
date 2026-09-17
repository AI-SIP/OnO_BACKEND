package com.aisip.OnO.backend.util.quartz;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

public class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

    /**
     * 부모에 ApplicationContext 를 넘겨야 잡을 {@code createBean} 으로 만든다.
     *
     * <p>예전에는 자기 필드만 채우고 부모를 부르지 않아, 부모가 리플렉션으로 인자 없는 생성자를 찾았다.
     * 그래서 생성자 주입을 쓰는 잡(ChallengeNotificationJob, StudyRoomWeeklyReportJob)은 발화할 때마다
     * NoSuchMethodException 으로 실패하고 트리거가 ERROR 로 남았다.
     * {@code createBean} 은 생성자 주입과 {@code @Autowired} 필드 주입을 모두 처리하므로
     * 따로 {@code autowireBean} 을 부르던 코드는 뺐다.
     */
    @Override
    public void setApplicationContext(ApplicationContext context) {
        super.setApplicationContext(context);
    }
}
