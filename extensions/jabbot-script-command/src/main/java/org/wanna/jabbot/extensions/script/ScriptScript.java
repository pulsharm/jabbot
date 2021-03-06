package org.wanna.jabbot.extensions.script;

import com.sipstacks.script.*;
import org.wanna.jabbot.command.Command;
import org.wanna.jabbot.command.messaging.CommandMessage;
import org.wanna.jabbot.command.parser.ArgsParser;
import org.wanna.jabbot.command.parser.QuotedStringArgParser;
import org.wanna.jabbot.messaging.DefaultMessageContent;
import org.wanna.jabbot.messaging.MessageContent;
import org.wanna.jabbot.messaging.body.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tsearle <tsearle>
 * @since 2015-03-20
 */
public class ScriptScript implements Command {

	com.sipstacks.script.Statement scriptCmd;
	String name;
	String author;
	ScriptCommand parent;

	public ScriptScript(String name, com.sipstacks.script.Statement scriptCmd, String author, ScriptCommand parent) {
		this.name = name;
		this.scriptCmd = scriptCmd;
		this.author = author;
		this.parent= parent;
	}

        @Override
        public ArgsParser getArgsParser() {
                return new QuotedStringArgParser();
        }

	public String getCommandName() {
		return name;
	}

	/**
	 * Returns a short description of the command
	 * @return String command description
	 */
	public String getDescription() { return null;}

	static void getFunctions(com.sipstacks.script.Statement cmd, StringBuffer sb) {
		List<Function> funcs = new ArrayList<Function>();
		cmd.getFunctions(funcs);
		for(Function func : funcs) {
			if(func instanceof StatementFunction) {
				StatementFunction stmtFunc = (StatementFunction)func;
				getFunctions(stmtFunc.getStatement(),sb);
				if (!(stmtFunc.getStatement() instanceof com.sipstacks.script.ExternalCommand)) {
					sb.append("local sub ");
					sb.append(stmtFunc.getName());
					sb.append(stmtFunc.getStatement().dump());
				}
			}
		}
	}	

	/**
	 * Returns the command help
	 * @return String help message
	 */
	public String getHelpMessage() { 
		StringBuffer sb = new StringBuffer();
		sb.append("Midori Script command written by " + author + "\n");
		getFunctions(scriptCmd, sb);
		sb.append("sub " + name + " \n" + scriptCmd.dump());
		return sb.toString();
	}

	public MessageContent process(CommandMessage message) {
		List<String> args = getArgsParser().parse(message.getArgsLine());
		OutputStream response = new OutputStream();
		parent.currentUser.set(message.getSender());

		try {
			scriptCmd.exec(response,args);
		} catch (ScriptParseException | ScriptFlowException e) {
			return new DefaultMessageContent(e.getMessage());
		}

		MessageContent messageContent = new DefaultMessageContent(response.getText());
		if (response.getHtml().length() > 0) {
			BodyPart xhtmlPart = new XhtmlBodyPart(response.getHtml());
			try {
				BodyPartValidator validator = BodyPartValidatorFactory.getInstance().create(BodyPart.Type.XHTML);
				if(validator != null){
					validator.validate(xhtmlPart);
				}
				messageContent.addBody(xhtmlPart);
			} catch (InvalidBodyPartException e) {
				StringBuilder errorMessage = new StringBuilder();
				errorMessage.append(e.getMessage());
				if(e.getInvalidBodyPart() != null){
					errorMessage.append("\n");
					errorMessage.append(e.getInvalidBodyPart().getText())
							.append("\n")
							.append(e.getCause().getMessage());

				}
				return new DefaultMessageContent(errorMessage.toString());
			}
		}

		return messageContent;
	}

	public void reset() {
		scriptCmd.reset();
	}
}

