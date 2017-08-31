package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Sample;

@Component
public class SampleArchiveAssignmentService implements ArchiveAssigner<Sample> {

    @Override
    public Archive assignArchive(Sample submittable) {
        return Archive.BioSamples;
    }
}
