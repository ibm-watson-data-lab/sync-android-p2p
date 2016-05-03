import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Server;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;

import com.cloudant.p2p.listener.HttpListener;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.notifications.ReplicationCompleted;
import com.cloudant.sync.notifications.ReplicationErrored;
import com.cloudant.sync.replication.ErrorInfo;
import com.cloudant.sync.replication.Replicator;
import com.google.common.eventbus.Subscribe;

public abstract class P2PAbstractTest {

	protected Map<Integer, String> databaseDirs = new HashMap<Integer, String>();

	private static void removeRecursive(Path path) throws IOException {
		Files.walkFileTree(path, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException
			{
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}
	
			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException
			{
				// try to delete the file anyway, even if its attributes
				// could not be read, since delete-only access is
				// theoretically possible
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}
	
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
			{
				if (exc == null)
				{
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
				else
				{
					// directory iteration failed; propagate exception
					throw exc;
				}
			}
		});
	}

	void createServer(final int port, final String dbname) throws Exception {
	
		final String databaseDir = Files.createTempDirectory(null).toAbsolutePath().toString();
		databaseDirs.put(port, databaseDir);
	
		DatastoreManager manager = new DatastoreManager(databaseDir);
		Datastore ds = manager.openDatastore(dbname);
		ds.close();
	
		Runnable r = new Runnable()
		{
			@Override
			public void run()
			{
				final Router router = new Router();
				router.attachDefault(HttpListener.class);
	
				org.restlet.Application myApp = new org.restlet.Application() {
					@Override
					public org.restlet.Restlet createInboundRoot() {
						Context ctx = getContext();
						ctx.getParameters().add("databaseDir", databaseDir);
						ctx.getParameters().add("port", Integer.toString(port));
						router.setContext(ctx);
						return router;
					};
				};
	
				Component component = new Component();
				component.getDefaultHost().attach("/", myApp);
				try {
					new Server(Protocol.HTTP, port, component).start();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
		new Thread(r).start();
	}

	@After
	public void tearDown() {
		// TODO nicely shutdown the Restlet servers 
		try {
			removeRecursive( (new File(databaseDirs.get(8182))).toPath() );
			removeRecursive( (new File(databaseDirs.get(8183))).toPath() );
		}
		catch (Exception e) {
			// ignore
		}
	}

	protected void waitForReplication(Replicator replicator) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		Listener listener = new Listener(latch);
		replicator.getEventBus().register(listener);
		replicator.start();
		latch.await();
		replicator.getEventBus().unregister(listener);
		if (replicator.getState() != Replicator.State.COMPLETE) {
			System.out.println("Error replicating TO remote");
			System.out.println(listener.error);
		} else {
			System.out.println(String.format("Replicated %d documents in %d batches",
					listener.documentsReplicated, listener.batchesReplicated));
		}
	}

	public P2PAbstractTest() {
		super();
	}
	
	/**
	 * A {@code ReplicationListener} that sets a latch when it's told the
	 * replication has finished.
	 */
	class Listener {

		private final CountDownLatch latch;
		public ErrorInfo error = null;
		public int documentsReplicated;
		public int batchesReplicated;

		Listener(CountDownLatch latch) {
			this.latch = latch;
		}

		@Subscribe
		public void complete(ReplicationCompleted event) {
			this.documentsReplicated = event.documentsReplicated;
			this.batchesReplicated = event.batchesReplicated;
			latch.countDown();
		}

		@Subscribe
		public void error(ReplicationErrored event) {
			this.error = event.errorInfo;
			latch.countDown();
		}
	}

}