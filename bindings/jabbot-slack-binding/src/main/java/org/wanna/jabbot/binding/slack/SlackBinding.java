package org.wanna.jabbot.binding.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sipstacks.xhml.Slack;
import com.sipstacks.xhml.XHTMLObject;
import com.sipstacks.xhml.XHtmlConvertException;
import allbegray.slack.SlackClientFactory;
import allbegray.slack.rtm.Event;
import allbegray.slack.rtm.EventListener;
import allbegray.slack.rtm.SlackRealTimeMessagingClient;
import allbegray.slack.type.Attachment;
import allbegray.slack.type.User;
import allbegray.slack.webapi.SlackWebApiClient;
import allbegray.slack.webapi.method.chats.ChatPostMessageMethod;
import emoji4j.EmojiManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wanna.jabbot.binding.AbstractBinding;
import org.wanna.jabbot.binding.ConnectionException;
import org.wanna.jabbot.binding.Room;
import org.wanna.jabbot.binding.config.BindingConfiguration;
import org.wanna.jabbot.binding.config.RoomConfiguration;
import org.wanna.jabbot.binding.event.ConnectedEvent;
import org.wanna.jabbot.binding.event.DisconnectedEvent;
import org.wanna.jabbot.binding.event.MessageEvent;
import org.wanna.jabbot.messaging.*;
import org.wanna.jabbot.messaging.body.BodyPart;

import java.util.List;

/**
 * @author vmorsiani <vmorsiani>
 * @since 2014-08-14
 */
public class SlackBinding extends AbstractBinding<Object> {
	private final Logger logger = LoggerFactory.getLogger(SlackBinding.class);
	private boolean connected;
	private SlackRealTimeMessagingClient rtmClient;
	private SlackWebApiClient webApiClient;
	static {
		EmojiManager.addStopWords(":[0-9]+");
	}

	public SlackBinding(BindingConfiguration configuration) {
		super(configuration);
	}

	@Override
	public boolean connect() {
		logger.info("Creating RTM Client");
		try {
			rtmClient = SlackClientFactory.createSlackRealTimeMessagingClient(getConfiguration().getPassword());
			webApiClient = SlackClientFactory.createWebApiClient(getConfiguration().getPassword());
		} catch (Throwable e) {
			throw new ConnectionException(e);
		}
		logger.info("RTM Client created! connecting");
		rtmClient.addListener(Event.MESSAGE, new EventListener() {
			@Override
			public void handleMessage(JsonNode jsonNode) {
				ObjectMapper mapper = new ObjectMapper();

				try {
					String json = mapper.writeValueAsString(jsonNode);
					logger.debug("json payload: "+json);
					allbegray.slack.type.Message slackMsg = mapper.treeToValue(jsonNode,allbegray.slack.type.Message.class);

					logger.info("Got a message: " + slackMsg.getText());
					String channelId = jsonNode.get("channel").asText();
					dispatchMessage(channelId,slackMsg);
				} catch (JsonProcessingException e) {
					logger.error("Failed to parse message",e);
				}
			}
		});

		try {
			rtmClient.connect();
			logger.info("RTP Connected");
			connected = true;
			super.dispatchEvent(new ConnectedEvent(this));
		}catch(Throwable e){
			throw new ConnectionException(e);
		}
		return connected;
	}

	@Override
	public boolean disconnect() {
		rtmClient.close();
		webApiClient.shutdown();
		return true;
	}

	@Override
	public Room joinRoom(RoomConfiguration configuration) {
		return null;
	}

	@Override
	public boolean isConnected() {
		if (connected) {
			connected = rtmClient.ping();
			if (!connected) {
				rtmClient.close();
				webApiClient.shutdown();
			}
		}
		return connected;
	}

	@Override
	public Room getRoom(String roomName) {
		return null;
	}

	@Override
	public void sendMessage(TxMessage response) {
		MessageContent messageContent = response.getMessageContent();
		ChatPostMessageMethod cpmm = new ChatPostMessageMethod(response.getDestination().getAddress(), "Formatted Response:");
		if(messageContent.getBody(BodyPart.Type.XHTML) != null) {
			XHTMLObject obj = new XHTMLObject();

			try {
				logger.info("About to convert:" + messageContent.getBody(BodyPart.Type.XHTML).getText());
				obj.parse(messageContent.getBody(BodyPart.Type.XHTML).getText());
				List<Attachment> attachments = Slack.convert(obj);
				attachments.get(0).setFallback(messageContent.getBody());
				cpmm.setAttachments(attachments);
			} catch (XHtmlConvertException e) {
				logger.error("Unable to parse xhtml!", e);
			}

		} else {
			if(messageContent.getBody().length() == 0) {
				cpmm.setText("^_^");
			} else {
				String body = messageContent.getBody();
				body = body.replaceAll("&", "&amp;");
				body = body.replaceAll("<", "&lt;");
				body = body.replaceAll(">", "&gt;");

				// repair special link types
				body = body.replaceAll("&lt;@([|0-9A-Za-z]+)&gt;", "<@$1>");
				body = body.replaceAll("&lt;#([|0-9A-Za-z]+)&gt;", "<#$1>");

				logger.info("Sending messageContent: " + body);

				cpmm.setText(body);
			}
		}
		cpmm.setUsername(this.getConfiguration().getUsername());
		cpmm.setAs_user(true);

		logger.info("Sending messageContent:" + cpmm.toString());
		webApiClient.postMessage(cpmm);
	}

	private void dispatchMessage(String channel, allbegray.slack.type.Message slackMsg) {
		String username = slackMsg.getUsername();

		if(username == null && slackMsg.getUser() == null) {
			logger.error("MessageContent missing user!" + slackMsg.toString());
			return;
		}

		if(username == null) {
			User user = webApiClient.getUserInfo(slackMsg.getUser());
			username = user.getName();
		}

		String slackMsgText = slackMsg.getText();
		slackMsgText = slackMsgText.replaceAll("<(http[^\"|>]*)[|]([^|\">]*)>", "$2");
		slackMsgText = slackMsgText.replaceAll("<(http[^\">]*)>", "$1");
		slackMsgText = slackMsgText.replace("&gt;", ">");
		slackMsgText = slackMsgText.replace("&lt;", "<");
		slackMsgText = slackMsgText.replace("&amp;", "&");

		RxMessage request = new DefaultRxMessage(new DefaultMessageContent(slackMsgText), new DefaultResource(channel,username));
		MessageEvent messageEvent = new MessageEvent(this, request);
		dispatchEvent(messageEvent);


	}

}
