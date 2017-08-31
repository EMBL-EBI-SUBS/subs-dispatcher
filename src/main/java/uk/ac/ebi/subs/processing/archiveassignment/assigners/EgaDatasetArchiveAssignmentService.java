package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.EgaDataset;

@Component
public class EgaDatasetArchiveAssignmentService implements ArchiveAssigner<EgaDataset> {


    @Override
    public Archive assignArchive(EgaDataset submittable) {
        return Archive.Ega;
    }
}
