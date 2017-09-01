package uk.ac.ebi.subs.processing.archiveassignment;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.subs.messaging.Queues;

@Configuration
public class QueueConfig {

    public static final String SUBMISSION_ARCHIVE_ASSIGNMENT = "usi-submission-archive-assignment";

    /**
     * Queue for submissions to receive archive assignments
     * @return
     */

    @Bean
    Queue archiveAssignmentQueue() {return new Queue(SUBMISSION_ARCHIVE_ASSIGNMENT,true);}

    @Bean
    Binding archiveAssignmentBinding(Queue archiveAssignmentQueue, TopicExchange submissionExchange){
        return BindingBuilder.bind(archiveAssignmentQueue).to(submissionExchange).with(Queues.SUBMISSION_SUBMITTED_ROUTING_KEY);
    }

}
