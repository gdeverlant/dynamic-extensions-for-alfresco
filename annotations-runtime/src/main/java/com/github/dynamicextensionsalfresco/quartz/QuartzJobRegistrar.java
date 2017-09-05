package com.github.dynamicextensionsalfresco.quartz;

import com.github.dynamicextensionsalfresco.jobs.ScheduledQuartzJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

/**
 * Created by jasper on 19/07/17.
 */
public class QuartzJobRegistrar implements ApplicationContextAware, InitializingBean, DisposableBean {
    private Logger logger = LoggerFactory.getLogger(QuartzJobRegistrar.class);

    @Autowired
    protected Scheduler scheduler;
    private ArrayList<ScheduledQuartzJob> registeredJobs = new ArrayList<ScheduledQuartzJob>();

    private ApplicationContext applicationContext;

    @Override
    public void afterPropertiesSet() throws ParseException, SchedulerException {
        Map<String, Object> scheduledBeans = applicationContext.getBeansWithAnnotation(ScheduledQuartzJob.class);
        for (Map.Entry entry : scheduledBeans.entrySet()) {
            Object bean = entry.getValue();
            Assert.isInstanceOf(Job.class, bean, "annotated Quartz job classes should implement org.quartz.Job");

            ScheduledQuartzJob annotation = bean.getClass().getAnnotation(ScheduledQuartzJob.class);

            try {
                String cron = applicationContext.getBean("global-properties", Properties.class).getProperty(annotation.cronProp(), annotation.cron());
                CronTrigger trigger = new CronTrigger(annotation.name(), annotation.group(), cron);
                JobDetail jobDetail = new JobDetail(annotation.name(), annotation.group(), GenericQuartzJob.class);
                jobDetail.getJobDataMap().put(GenericQuartzJob.Companion.getBEAN_ID(), bean);
                scheduler.scheduleJob(jobDetail, trigger);

                registeredJobs.add(annotation);

                logger.debug("scheduled job " + annotation.name() + " from group " + annotation.group() + " using cron " + annotation.cron());
            } catch (Exception e) {
                logger.error("failed to register job " + annotation.name() + " using cron " + annotation.group(), e);
            }

        }
    }

    @Override
    public void destroy() throws SchedulerException {
        for (ScheduledQuartzJob job : registeredJobs) {
            try {
                scheduler.unscheduleJob(job.name(), job.group());
                logger.debug("unscheduled job " + job.name() + " from group " + job.group());
            } catch (SchedulerException e) {
                logger.error("failed to cleanup quartz job " + job, e);
            }
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

}
