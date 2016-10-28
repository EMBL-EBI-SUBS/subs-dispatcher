package uk.ac.ebi.subs.dispatcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.DispatcherApplication;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.SubmissionEnvelope;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Study;
import uk.ac.ebi.subs.messaging.Exchanges;
import uk.ac.ebi.subs.messaging.Queues;
import uk.ac.ebi.subs.messaging.Topics;

import java.util.Date;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = DispatcherApplication.class)
public class DispatchProcessorTest {

    int messagesToEna = 0;
    int messagesToBioSamples = 0;
    int messagesToAe = 0;


    SubmissionEnvelope subEnv;
    Submission sub;
    Sample sample;
    Study enaStudy;
    Study aeStudy;


    @Autowired
    RabbitMessagingTemplate rabbitMessagingTemplate;

    @Autowired
    MessageConverter messageConverter;

    @Autowired
    DispatchProcessor dispatchProcessor;

    @Before
    public void setUp() {
        this.rabbitMessagingTemplate.setMessageConverter(this.messageConverter);

        this.messagesToEna = 0;
        this.messagesToBioSamples = 0;
        this.messagesToAe = 0;

        sub = new Submission();
        sub.setId("DispatchTestSub");
        sub.getSubmitter().setEmail("test@ebi.ac.uk");
        sub.getDomain().setName("testDomain");
        sub.setSubmissionDate(new Date());

        sample = new Sample();
        sample.setId("1");
        sample.setArchive(Archive.Usi);

        sub.getSamples().add(sample);

        enaStudy = new Study();
        enaStudy.setId("2");
        enaStudy.setArchive(Archive.Ena);

        sub.getStudies().add(enaStudy);

        aeStudy = new Study();
        aeStudy.setId("3");
        aeStudy.setArchive(Archive.ArrayExpress);

        sub.getStudies().add(aeStudy);

        subEnv = new SubmissionEnvelope(sub);
        System.out.println(subEnv);
    }

    @Test
    public void testTheLoop() throws InterruptedException {
        //TODO these messages are received with a null submission in the envelope
        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, Topics.EVENT_SUBMISSION_SUBMITTED, subEnv);



        sample.setAccession("SAMPLE1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, Topics.EVENT_SUBMISSION_UPDATED, subEnv);


        enaStudy.setAccession("ENA1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, Topics.EVENT_SUBMISSION_UPDATED, subEnv);

        aeStudy.setAccession("AE1");

        rabbitMessagingTemplate.convertAndSend(Exchanges.SUBMISSIONS, Topics.EVENT_SUBMISSION_UPDATED, subEnv);


        Thread.sleep(500);
        System.out.println("BioSamples messages: " + messagesToBioSamples);
        System.out.println("ENA messages: " + messagesToEna);
        System.out.println("AE messages: " + messagesToAe);

    }


    @Test
    public void testSupportingSamples() {

        Submission submission = new Submission();
        SubmissionEnvelope envelope = new SubmissionEnvelope(submission);

        Assay a = new Assay();

        SampleRef sr = new SampleRef();
        sr.setArchive(Archive.Usi.name());
        sr.setAlias("bob");
        sr.setAlias("S1");

        a.getSampleUses().add(new SampleUse(sr));

        submission.getAssays().add(a);

        dispatchProcessor.determineSupportingInformationRequired(envelope);

        assertThat("supporting info requirement identified ", envelope.getSupportingSamplesRequired(), hasSize(1));
        assertThat("supporting info not filled out yet", envelope.getSupportingSamples(), empty());


    }


    @RabbitListener(queues = Queues.ENA_AGENT)
    public void handleEna(SubmissionEnvelope submissionEnvelope) {
        synchronized (this) {
            this.messagesToEna++;
            System.out.println("ENA!");
        }
    }

    @RabbitListener(queues = Queues.BIOSAMPLES_AGENT)
    public void handleSamples(SubmissionEnvelope submissionEnvelope) {
        synchronized (this) {
            this.messagesToBioSamples++;
            System.out.println("BioSamples!");
        }
    }

    @RabbitListener(queues = Queues.AE_AGENT)
    public void handleAe(SubmissionEnvelope submissionEnvelope) {
        synchronized (this) {
            this.messagesToAe++;
            System.out.println("AE!");
        }
    }


}
