package uk.ac.ebi.subs.dispatcher;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.submittables.*;

import java.util.stream.Stream;

@Service
public class SubmissionEnvelopeServiceImpl implements SubmissionEnvelopeService {

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    AnalysisRepository analysisRepository;
    @Autowired
    AssayDataRepository assayDataRepository;
    @Autowired
    AssayRepository assayRepository;
    @Autowired
    EgaDacPolicyRepository egaDacPolicyRepository;
    @Autowired
    EgaDacRepository egaDacRepository;
    @Autowired
    EgaDatasetRepository egaDatasetRepository;
    @Autowired
    ProjectRepository projectRepository;
    @Autowired
    ProtocolRepository protocolRepository;
    @Autowired
    SampleGroupRepository sampleGroupRepository;
    @Autowired
    SampleRepository sampleRepository;
    @Autowired
    StudyRepository studyRepository;

    @Override
    public SubmissionEnvelope fetchOne(String submissionId) {
        Submission minimalSub = submissionRepository.findOne(submissionId);

        if (minimalSub == null) {
            throw new ResourceNotFoundException();
        }

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(minimalSub);

        submissionEnvelope.getAnalyses().addAll(analysisRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getAssayData().addAll(assayDataRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getAssays().addAll(assayRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDacPolicies().addAll(egaDacPolicyRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDacs().addAll(egaDacRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDatasets().addAll(egaDatasetRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getProjects().addAll(projectRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getProtocols().addAll(protocolRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSampleGroups().addAll(sampleGroupRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSamples().addAll(sampleRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getStudies().addAll(studyRepository.findBySubmissionId(submissionId));

        return submissionEnvelope;
    }

    @Override
    public Stream<? extends StoredSubmittable> submissionContents(String submissionId) {
        Submission minimalSub = submissionRepository.findOne(submissionId);

        if (minimalSub == null) {
            throw new ResourceNotFoundException();
        }

        return Stream.of(
                analysisRepository,
                assayDataRepository,
                assayRepository,
                egaDacPolicyRepository,
                egaDacRepository,
                egaDatasetRepository,
                projectRepository,
                protocolRepository,
                sampleGroupRepository,
                sampleRepository,
                studyRepository
        ).flatMap(repo -> repo.streamBySubmissionId(submissionId));
    }
}
