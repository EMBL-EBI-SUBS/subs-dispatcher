package uk.ac.ebi.subs.processing.archiveassigner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.DispatcherApplication;
import uk.ac.ebi.subs.data.component.*;
import uk.ac.ebi.subs.processing.archiveassignment.SubmissionArchiveAssignmentService;
import uk.ac.ebi.subs.repository.model.*;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.ProjectRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmissionHelperService;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = DispatcherApplication.class)
public class ArchiveAssignmentTest {


    private Sample sample;
    private Study study;
    private Assay assay;
    private Project project;

    @Autowired
    private SubmissionArchiveAssignmentService submissionArchiveAssignmentService;

    @Before
    public void setUp() {
        Submission submission = submissionHelperService.createSubmission(team, submitter);
        project = createProject("testProject", submission);
        sample = createSample("testSample", submission);
        study = createStudy("testStudy", submission, StudyDataType.Proteomics);
        assay = createAssay("testAssay", submission, sample, study);
        submissionArchiveAssignmentService.assignArchives(submission);
    }

    @Test
    public void givenSample_assignBioSamples() {
        String archive = extractArchive(sample);

        assertThat(archive, equalTo(Archive.BioSamples.name()));
    }

    @Test
    public void givenProject_assignBioStudies(){
        String archive = extractArchive(project);

        assertThat(archive,equalTo(Archive.BioStudies.name()));
    }

    @Test
    public void givenProteomicsStudy_assignPride() {
        String archive = extractArchive(study);

        assertThat(archive, equalTo(Archive.Pride.name()));
    }

    @Test
    public void givenProteomicsAssay_assignPride() {
        String archive = extractArchive(assay);

        assertThat(archive, equalTo(Archive.Pride.name()));

    }

    private String extractArchive(StoredSubmittable storedSubmittable) {
        ProcessingStatus processingStatus = processingStatusRepository.findOne(storedSubmittable.getProcessingStatus().getId());
        return processingStatus.getArchive();
    }

    private Team team = Team.build("tester1");
    private Submitter submitter = Submitter.build("alice@test.ac.uk");

    public Sample createSample(String alias, Submission submission) {
        Sample s = new Sample();
        s.setAlias(alias);
        s.setSubmission(submission);
        submittableHelperService.setupNewSubmittable(s);
        sampleRepository.insert(s);
        return s;
    }

    public Study createStudy(String alias, Submission submission, StudyDataType studyDataType) {
        Study s = new Study();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setProjectRef(null);
        s.setStudyType(studyDataType);
        submittableHelperService.setupNewSubmittable(s);
        studyRepository.insert(s);
        return s;
    }

    public Assay createAssay(String alias, Submission submission, Sample sample, Study study) {
        Assay a = new Assay();
        a.setAlias(alias);
        a.setSubmission(submission);

        submittableHelperService.setupNewSubmittable(a);
        a.setStudyRef((StudyRef) study.asRef());

        assayRepository.insert(a);
        return a;

    }

    public Project createProject(String alias, Submission submission) {
        Project project = new Project();
        project.setAlias(alias);
        project.setSubmission(submission);

        submittableHelperService.setupNewSubmittable(project);

        projectRepository.insert(project);
        return project;
    }


    @Autowired
    private SubmissionHelperService submissionHelperService;

    @Autowired
    private SubmittableHelperService submittableHelperService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private AssayRepository assayRepository;

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Autowired
    private ProjectRepository projectRepository;


}
