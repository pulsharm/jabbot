package org.wanna.jabbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wanna.jabbot.binding.BindingListener;
import org.wanna.jabbot.binding.BindingMessage;
import org.wanna.jabbot.binding.JabbotConnection;
import org.wanna.jabbot.command.Command;
import org.wanna.jabbot.command.CommandNotFoundException;
import org.wanna.jabbot.command.MessageWrapper;
import org.wanna.jabbot.command.parser.CommandParser;
import org.wanna.jabbot.command.parser.CommandParsingResult;

import java.util.List;

/**
 * @author vmorsiani <vmorsiani>
 * @since 2015-02-20
 */
public class JabbotBindingListener implements BindingListener{
	final Logger logger = LoggerFactory.getLogger(JabbotBindingListener.class);
	private final CommandParser commandParser;
	private final String commandPrefix;

	public JabbotBindingListener(String commandPrefix) {
		this.commandPrefix = commandPrefix;
		commandParser = new DefaultCommandParser(commandPrefix);
	}

	@Override
	public void onMessage(JabbotConnection binding, BindingMessage message) {
		if(message == null || !message.getBody().startsWith(commandPrefix)){
			return;
		}

		logger.debug("[JABBOT] received message on {}: {}",message.getRoomName(),message.getBody());
		CommandParsingResult result = commandParser.parse(message.getBody());

		try {
			Command command = binding.getCommandFactory().create(result.getCommandName());
			List<String> args = command.getArgsParser().parse(result.getRawArgsLine());
			MessageWrapper wrapper = new MessageWrapper(message);
			wrapper.setArgs(args);
			wrapper.setSender(message.getSender());
			command.process(binding.getRoom(message.getRoomName()), wrapper);
		} catch (CommandNotFoundException e) {
			logger.debug("command not found: '{}'", e.getCommandName());
		}
	}
}
