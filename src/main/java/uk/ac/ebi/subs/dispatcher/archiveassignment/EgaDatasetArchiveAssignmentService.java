package uk.ac.ebi.subs.dispatcher.archiveassignment;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.EgaDataset;

@Component
public class EgaDatasetArchiveAssignmentService implements ArchiveAssignmentService<EgaDataset> {


    @Override
    public Archive assignArchive(EgaDataset submittable) {
        return Archive.Ega;
    }
}
