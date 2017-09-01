package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.StudyRef;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;

@Component
public class AssayArchiveAssignmentService implements ArchiveAssigner<Assay> {

    private StudyRepository studyRepository;
    private StudyArchiveAssignmentService studyArchiveAssignmentService;
    public AssayArchiveAssignmentService(StudyRepository studyRepository, StudyArchiveAssignmentService studyArchiveAssignmentService) {
        this.studyRepository = studyRepository;
        this.studyArchiveAssignmentService = studyArchiveAssignmentService;
    }

    @Override
    public Archive assignArchive(Assay submittable) {
        StudyRef studyRef = submittable.getStudyRef();
        Study study;
        if (studyRef.isAccessioned()) {
            study = studyRepository.findFirstByAccessionOrderByCreatedDateDesc(studyRef.getAccession());
        } else {
            study = studyRepository.findFirstByTeamNameAndAliasOrderByCreatedDateDesc(
                    studyRef.getTeam(), studyRef.getAlias()
            );
        }
        return studyArchiveAssignmentService.assignArchive(study);
    }
}
