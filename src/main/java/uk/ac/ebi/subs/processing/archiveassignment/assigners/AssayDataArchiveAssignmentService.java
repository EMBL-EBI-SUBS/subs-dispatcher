package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.AssayRef;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.AssayData;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;

@Component
public class AssayDataArchiveAssignmentService implements ArchiveAssigner<AssayData> {

    private AssayArchiveAssignmentService assayArchiveAssignmentService;
    private AssayRepository assayRepository;

    public AssayDataArchiveAssignmentService(AssayArchiveAssignmentService assayArchiveAssignmentService, AssayRepository assayRepository) {
        this.assayArchiveAssignmentService = assayArchiveAssignmentService;
        this.assayRepository = assayRepository;
    }

    @Override
    public Archive assignArchive(AssayData submittable) {
        AssayRef assayRef = submittable.getAssayRef();
        Assay assay;
        if (assayRef.isAccessioned()) {
            assay = assayRepository.findFirstByAccessionOrderByCreatedDateDesc(assayRef.getAccession());
        } else {
            assay = assayRepository.findFirstByTeamNameAndAliasOrderByCreatedDateDesc(
                    assayRef.getTeam(), assayRef.getAlias()
            );
        }
        return assayArchiveAssignmentService.assignArchive(assay);
    }
}
