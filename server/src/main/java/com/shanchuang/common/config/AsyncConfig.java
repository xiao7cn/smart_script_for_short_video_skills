package com.shanchuang.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池。
 *
 * 生成一条文案要 3 次模型调用（选题标题 + 正文 + 去味），一批 20 条就是 60 次，
 * 所以任务必须异步跑、前端轮询。批内并发另由 Semaphore 控制，见 TaskRunner。
 */
@Configuration
public class AsyncConfig {

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sc-task-");
        // 队列满了宁可让调用线程自己跑，也不丢任务——额度已经预扣了
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 批内条目并发用的池，与任务池分开，避免条目任务把任务池占满导致新任务饿死 */
    @Bean("itemExecutor")
    public ThreadPoolTaskExecutor itemExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(400);
        executor.setThreadNamePrefix("sc-item-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
