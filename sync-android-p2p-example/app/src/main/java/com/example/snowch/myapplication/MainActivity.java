package com.example.snowch.myapplication;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;

import com.cloudant.p2p.listener.HttpListener;
import com.cloudant.sync.datastore.Datastore;
import com.cloudant.sync.datastore.DatastoreManager;

import org.restlet.Component;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.restlet.Context;
import org.restlet.Server;

import android.util.Log;

import java.io.File;
import java.util.logging.Level;


public class MainActivity extends Activity {
	
	final int PORT = 8182;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        AndroidLoggingHandler.reset(new AndroidLoggingHandler());
        java.util.logging.Logger.getLogger(HttpListener.class.getCanonicalName()).setLevel(Level.FINEST);

        try {
            // create datastore
            final File databaseDir = getApplicationContext().getDir("datastores", MODE_PRIVATE);
            final DatastoreManager manager = new DatastoreManager(databaseDir.getAbsolutePath());

            Datastore ds = manager.openDatastore("mydb");
            ds.close();

            // Set up a Restlet service
            final Router router = new Router();
            router.attachDefault(HttpListener.class);

            org.restlet.Application myApp = new org.restlet.Application() {
                @Override
                public org.restlet.Restlet createInboundRoot() {
                	Context ctx = getContext();
    				ctx.getParameters().add("databaseDir", databaseDir.getAbsolutePath());
    				ctx.getParameters().add("port", Integer.toString(PORT));
    				router.setContext(ctx);
                    return router;
                };
            };
            Component component = new Component();
            component.getDefaultHost().attach("/", myApp);

            new Server(Protocol.HTTP, PORT, component).start();
            
        } catch (Exception e) {
        	Log.e("snowch.MyActivity", "Error", e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
