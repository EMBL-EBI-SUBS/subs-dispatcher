package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.EgaDacPolicy;

@Component
public class EgaDacPolicyArchiveAssignmentService implements ArchiveAssigner<EgaDacPolicy> {

    @Override
    public Archive assignArchive(EgaDacPolicy submittable) {
        return Archive.Ega;
    }
}
