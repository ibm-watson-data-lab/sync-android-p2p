import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;
import com.cloudant.sync.datastore.DocumentBodyFactory;
import com.cloudant.sync.datastore.DocumentRevision;
import com.cloudant.sync.replication.Replicator;
import com.cloudant.sync.replication.ReplicatorBuilder;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;


public class P2PTest extends P2PAbstractTest {
	
	final static String IP_ADDR = "127.0.0.1";
	
	final static int SRC_PORT = 8182;

	final static int DST_PORT = 8184;
	
	@Before
	public void setup() throws Exception {
		createServer(SRC_PORT, "source");
		createServer(DST_PORT, "target");
		
		// wait for servers to start - TODO there's probably a better way we should be doing this
		Thread.sleep(1000);
	}
	
	@Test
	public void testPush() throws Exception {
		
		DocumentRevision sourceRev = null;

		// create a document in the source database
		
		try {
			URI dstUri = new URI("http://" + IP_ADDR + ":" + DST_PORT + "/target");
			
			DatastoreManager sourceManager = DatastoreManager.getInstance(databaseDirs.get(SRC_PORT));
			Datastore sourceDs = sourceManager.openDatastore("source");
	
			DocumentRevision revToCreate = new DocumentRevision();
			Map<String, Object> json = new HashMap<String, Object>();
			json.put("description", "Buy milk");
			revToCreate.setBody(DocumentBodyFactory.create(json));
			sourceRev = sourceDs.createDocumentFromRevision(revToCreate);
	
			Replicator replicator = ReplicatorBuilder.push().from(sourceDs).to(dstUri).build();
			waitForReplication(replicator);
		}
		catch (Exception e) {
			fail("Unexpected exception: " + e.getMessage());
		}
		
		
		// check replication worked - does document exist in target?
		
		try {
			String targetDb = databaseDirs.get(DST_PORT);
			DatastoreManager destManager = DatastoreManager.getInstance(targetDb);
			Datastore destDs = destManager.openDatastore("target");
			Assert.assertTrue(
					"Source document " + 
							sourceRev.getId() + " " + 
							sourceRev.getRevision() + " was not found in target database " +
							targetDb + "/" + destDs.getDatastoreName() + "/db.sync",
							
					destDs.containsDocument(sourceRev.getId(), sourceRev.getRevision())
					);
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Unexpected exception: " + e.getMessage());
		}
	}
}
