package uk.ac.ebi.subs.dispatcher.archiveassignment;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.Sample;

@Component
public class SampleArchiveAssignmentService implements ArchiveAssignmentService<Sample> {

    @Override
    public Archive assignArchive(Sample submittable) {
        return Archive.BioSamples;
    }
}
