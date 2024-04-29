import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

public class GitHandler {
    private Git git;
    private UsernamePasswordCredentialsProvider credentialsProvider;

    public GitHandler(String gitUrl, String username, String password, String directory) {
        this.credentialsProvider = new UsernamePasswordCredentialsProvider(username, password);
        File tsDirectory = new File(directory);
        File gitDirectory = new File(tsDirectory, ".git");

        try {
            if (gitDirectory.exists()) {
                System.out.println("Opening existing repository");
                Repository existingRepo = new FileRepository(gitDirectory);
                this.git = new Git(existingRepo);
                this.git.pull().setCredentialsProvider(this.credentialsProvider).call();
                System.out.println("Repository updated");
            } else {
                System.out.println("Initializing repository");
                this.git = Git.cloneRepository()
                        .setURI(gitUrl)
                        .setDirectory(tsDirectory)
                        .setCredentialsProvider(this.credentialsProvider)
                        .call();
                System.out.println("Repository initialized");
            }
        } catch (GitAPIException | IOException e) {
            System.out.println("Error initializing repository, is it a valid URL?");
            e.printStackTrace();
        }
    }

    public void commitChanges(String commitMessage) {
        try {
            this.git.commit().setMessage(commitMessage).call();
            System.out.println("Changes committed");
        } catch (GitAPIException e) {
            System.out.println("Error committing changes");
            e.printStackTrace();
        }
    }

    public void addChanges() {
        try {
            this.git.add().addFilepattern(".").call();
            System.out.println("Changes added");
        } catch (GitAPIException e) {
            System.out.println("Error adding changes");
            e.printStackTrace();
        }
    }

    public void pushChanges() {
        try {
            Iterable<PushResult> results = this.git.push().setCredentialsProvider(this.credentialsProvider).call();
            results.forEach(result -> System.out.println("Pushed to " + result.getURI()));
        } catch (GitAPIException e) {
            System.out.println("Error pushing changes");
            e.printStackTrace();
        }
    }
}