package uk.ac.ebi.subs.dispatcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.DispatcherApplication;

import uk.ac.ebi.subs.data.component.*;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.*;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = DispatcherApplication.class)
public class DispatcherServiceTest {



    @Test
    public void testTheLoop() throws InterruptedException {

        Map<Archive,SubmissionEnvelope> firstOutput = dispatcherService.assessDispatchReadiness(submission);

        assertThat(firstOutput.keySet(),hasSize(1));
        assertThat(firstOutput.containsKey(Archive.BioSamples),is(true));

        setAccessionAndMarkComplete("SAMPLE1", sample);
        sampleRepository.save(sample);


        Map<Archive,SubmissionEnvelope> secondOutput = dispatcherService.assessDispatchReadiness(submission);

        assertThat(secondOutput.keySet(),hasSize(1));
        assertThat(secondOutput.containsKey(Archive.Ena),is(true));

        setAccessionAndMarkComplete("STUDY1", enaStudy);
        setAccessionAndMarkComplete("ASSAY1", enaAssay);
        studyRepository.save(enaStudy);
        assayRepository.save(enaAssay);

        Map<Archive,SubmissionEnvelope> thirdOutput = dispatcherService.assessDispatchReadiness(submission);

        assertThat(thirdOutput.keySet(),hasSize(1));
        assertThat(thirdOutput.containsKey(Archive.ArrayExpress),is(true));


        setAccessionAndMarkComplete("STUDY2", aeStudy);
        setAccessionAndMarkComplete("ASSAY2", aeAssay);
        studyRepository.save(aeStudy);
        assayRepository.save(aeAssay);


        Map<Archive,SubmissionEnvelope> fourthOutput = dispatcherService.assessDispatchReadiness(submission);

        assertThat(fourthOutput.keySet(),hasSize(0));

    }

    private void setAccessionAndMarkComplete(String accession, StoredSubmittable storedSubmittable) {
        storedSubmittable.setAccession(accession);
        storedSubmittable.getProcessingStatus().setAccession(storedSubmittable.getAccession());
        storedSubmittable.getProcessingStatus().setStatus(ProcessingStatusEnum.Completed);
        processingStatusRepository.save(storedSubmittable.getProcessingStatus());

    }


    @Autowired
    DispatcherService dispatcherService;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    SubmissionStatusRepository submissionStatusRepository;

    @Autowired
    SampleRepository sampleRepository;

    @Autowired
    StudyRepository studyRepository;

    @Autowired
    AssayRepository assayRepository;

    @Autowired
    ProcessingStatusRepository processingStatusRepository;

    Submission submission;
    Sample sample;
    Study enaStudy,aeStudy;
    Assay enaAssay,aeAssay;


    @Before
    public void buildUp(){
        tearDown();

        submission = new Submission();
        submission.setId("testId");
        submission.setSubmissionStatus(new SubmissionStatus(SubmissionStatusEnum.Draft));

        submission.setTeam(Team.build("testerTeam"));
        submission.setSubmitter(Submitter.build("bob@test.ac.uk"));

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        sample = buildSample(Archive.BioSamples);
        aeStudy = buildStudy(Archive.ArrayExpress);
        enaStudy = buildStudy(Archive.Ena);
        enaAssay = buildAssay(Archive.Ena, sample, enaStudy);
        aeAssay = buildAssay(Archive.ArrayExpress, sample, aeStudy);

    }

    private Sample buildSample(Archive archive) {
        Sample sample = new Sample();

        sample.setTeam(submission.getTeam());
        sample.setArchive(archive);
        sample.setAlias("sample1");

        sample.setSubmission(submission);
        ProcessingStatus.createForSubmittable(sample);


        processingStatusRepository.save(sample.getProcessingStatus());
        sampleRepository.save(sample);
        return sample;
    }

    private Assay buildAssay(Archive archive, Sample sample, Study study) {
        Assay assay = new Assay();

        assay.setTeam(submission.getTeam());
        assay.setArchive(archive);
        assay.setAlias(UUID.randomUUID().toString());

        assay.setSubmission(submission);
        ProcessingStatus.createForSubmittable(assay);

        assay.setStudyRef((StudyRef)study.asRef());
        assay.getSampleUses().add(new SampleUse((SampleRef)sample.asRef()));

        processingStatusRepository.save(assay.getProcessingStatus());
        assayRepository.save(assay);

        return assay;
    }

    private Study buildStudy(Archive archive) {
        Study study = new Study();

        study.setAlias(UUID.randomUUID().toString());
        study.setTeam(submission.getTeam());
        study.setArchive(archive);

        study.setSubmission(submission);
        ProcessingStatus.createForSubmittable(study);

        processingStatusRepository.save(study.getProcessingStatus());
        studyRepository.save(study);

        return study;
    }

    @After
    public void tearDown(){
        Stream.of(
                submissionRepository,
                submissionStatusRepository,
                assayRepository,
                sampleRepository,
                studyRepository
        ).forEach(repo -> repo.deleteAll());
    }


}
