package ro.ridelance.anafvalidator.core;

import java.nio.file.Path;

/** Directorul temporar al unei cereri. Se folosește în try-with-resources: {@code close()} îl șterge. */
public final class Workspace implements AutoCloseable {

    static final String XML = "declaratie.xml";
    static final String RESULT = "rezultat.txt";
    static final String PDF = "declaratie.pdf";
    static final String STDOUT = "stdout.txt";

    private final Path dir;
    private final WorkspaceManager manager;

    Workspace(Path dir, WorkspaceManager manager) {
        this.dir = dir;
        this.manager = manager;
    }

    public Path dir() {
        return dir;
    }

    public Path xml() {
        return dir.resolve(XML);
    }

    public Path result() {
        return dir.resolve(RESULT);
    }

    public Path pdf() {
        return dir.resolve(PDF);
    }

    public Path stdout() {
        return dir.resolve(STDOUT);
    }

    @Override
    public void close() {
        manager.delete(dir);
    }
}
