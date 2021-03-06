package org.wanna.jabbot.binding.spark;

import com.ciscospark.Person;
import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wanna.jabbot.binding.AbstractBinding;
import org.wanna.jabbot.binding.Room;
import org.wanna.jabbot.binding.config.BindingConfiguration;
import org.wanna.jabbot.binding.config.RoomConfiguration;
import java.net.URI;
import java.util.Hashtable;
import java.util.Iterator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wanna.jabbot.binding.event.ConnectedEvent;
import org.wanna.jabbot.messaging.TxMessage;

/**
 * @author tsearle <tsearle>
 * @since 2016-04-08
 */
public class SparkBinding extends AbstractBinding<Object> {
	private final Logger logger = LoggerFactory.getLogger(SparkBinding.class);
	private Hashtable<String,SparkRoom> roomMap = null;
	com.ciscospark.Spark spark = null;
	boolean useWebhook = false;
	String webhookUrl = "";
	com.ciscospark.SparkServlet sparkServlet = null;
	private boolean connected;
	RoomPoller poller;
	Person me = null;

	public SparkBinding(BindingConfiguration configuration) {
		super(configuration);
		roomMap = new Hashtable<String,SparkRoom>();

		if (configuration.getParameters() != null) {
			if (configuration.getParameters().containsKey("use_webhook")) {
				useWebhook = (boolean)configuration.getParameters().get("use_webhook");
			}
			if (configuration.getParameters().containsKey("webhook_url")) {
				webhookUrl = (String)configuration.getParameters().get("webhook_url");
			}
		}
	}

	@Override
	public boolean connect() {
		spark = com.ciscospark.Spark.builder()
			.baseUrl(URI.create(getConfiguration().getUrl()))
			.accessToken(getConfiguration().getPassword())
			.build();
		if(useWebhook) {
			Server server = new Server(8080);
			ServletHandler context = new ServletHandler();
			server.setHandler(context);

			sparkServlet = new com.ciscospark.SparkServlet();
			context.addServletWithMapping(new ServletHolder(sparkServlet), "/*");

			try {
				server.start();
			} catch (Exception e) {
				logger.error("Unable to start server: ", e);
				return false;
			}

			// first cleanup old hooks
			Iterator<com.ciscospark.Webhook> webhooks = spark.webhooks().iterate();
			while(webhooks.hasNext()) {
				com.ciscospark.Webhook hk  = webhooks.next();
				logger.info("deleting webhook " + hk.getName() + " id: " + hk.getId());
				spark.webhooks().path("/"+hk.getId()).delete();
			}

			com.ciscospark.Webhook hook = new com.ciscospark.Webhook();
			hook.setName("midori hook");
			hook.setTargetUrl(URI.create(webhookUrl));
			hook.setResource("messages");
			hook.setEvent("created");
			hook = spark.webhooks().post(hook);
			logger.info("created webhook " + hook.getName());
			sparkServlet.addListener( new com.ciscospark.WebhookEventListener() {
				public void onEvent(com.ciscospark.WebhookEvent event) {
					com.ciscospark.Message msg = event.getData();
					String roomId = event.getData().getRoomId();
					SparkRoom sr = roomMap.get(roomId);
					if(roomMap.get(roomId) == null) {
						sr = new SparkRoom(SparkBinding.this, listeners);
						sr.create(roomId);
						roomMap.put(roomId,sr);
					}
					if (msg.getText() == null) {
						logger.info("Getting full message for " + event.getData().getId());
						msg = spark.messages().path("/" + event.getData().getId()).get();
					} else {
						logger.info("MessageContent already in webhook, delivering");
					}
					sr.dispatchMessage(msg);

				}
			});

			connected = true;
		}else{
			connected = true;
		}

		me = spark.people().path("/me").get();
		logger.info("I am " + me.getDisplayName() + " at " + me.getId());

		Iterator<com.ciscospark.Room> rooms = spark.rooms().iterate();

		while (rooms.hasNext()) {
			com.ciscospark.Room room = rooms.next();
			SparkRoom sr = new SparkRoom(this,listeners);
			if(sr.create(room)) {
				roomMap.put(room.getId(),sr);
			}
		}

		if(!useWebhook) {
			poller = new RoomPoller();
			poller.start();
		}

		if(connected == true){
			super.dispatchEvent(new ConnectedEvent(this));
		}

		return connected;
	}

	@Override
	public boolean disconnect() {
		if (poller != null) {
			poller.abort();
			poller.interrupt();
			poller = null;
		}
		connected = false;
		return true;
	}

	@Override
	public void sendMessage(TxMessage response) {
		String id = response.getDestination().getAddress();
		SparkRoom sr = roomMap.get(id);
		if(sr != null) {
			sr.sendMessage(response);
		}

	}

	@Override
	public Room joinRoom(RoomConfiguration configuration) {
		return null;
	}

	@Override
	public boolean isConnected() {
		if(useWebhook) {
			return connected;
		} else {
			return connected;
		}
	}

	@Override
	public Room getRoom(String roomName) {
		return null;
	}

	private class RoomPoller extends Thread {
		private boolean running;
		RoomPoller() {
			super("Spark Room Poller");
		}

		@Override
		public void run() {
			running = true;
			while (running) {
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					running = false;
				}
				logger.debug("Running room poll");
				try {
					Iterator<com.ciscospark.Room> rooms = spark.rooms().iterate();

					while (rooms.hasNext()) {
						com.ciscospark.Room room = rooms.next();
						if (roomMap.get(room.getId()) == null) {
							SparkRoom sr = new SparkRoom(SparkBinding.this, listeners);
							if (sr.create(room)) {
								roomMap.put(room.getId(), sr);
								sr.sendMessage(null, "^_^", null);
							}
						}
					}
				} catch (Exception e) {
					logger.error("Room Poller: got an exception", e);
				}
			}
		}

		public void abort(){
			running = false;
		}
	}
}
