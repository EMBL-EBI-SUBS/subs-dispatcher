package uk.ac.ebi.subs.dispatcher;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class DispatcherQueueConfig {

    /**
     * Queue for submissions to be checked for dispatch to archive agents
     * @return
     */
    @Bean
    Queue dispatcherQueue() {
        return new Queue(Queues.SUBMISSION_DISPATCHER,true);
    }

    @Bean
    Binding dispatcherProcessingUpdatedBinding(Queue dispatcherQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(dispatcherQueue).to(submissionExchange).with(Queues.SUBMISSION_PROCESSING_UPDATED_DISPATCHER_ROUTING_KEY);
    }

    @Bean
    Binding dispatcherSubmittedBinding(Queue dispatcherQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(dispatcherQueue).to(submissionExchange).with(Queues.SUBMISSION_SUBMITTED_ROUTING_KEY);
    }


    /**
     * Queue for submissions to be checked to see if they need supporting info
     * @return
     */
    @Bean Queue onSubmitCheckForSupportingInfoQueue() {return new Queue(Queues.SUBMISSION_SUBMITTED_CHECK_SUPPORTING_INFO,true); }


    @Bean
    Binding suppInfoBinding(Queue onSubmitCheckForSupportingInfoQueue, TopicExchange submissionExchange) {
        return BindingBuilder.bind(onSubmitCheckForSupportingInfoQueue).to(submissionExchange).with(Queues.SUBMISSION_SUBMITTED_ROUTING_KEY);
    }
}
