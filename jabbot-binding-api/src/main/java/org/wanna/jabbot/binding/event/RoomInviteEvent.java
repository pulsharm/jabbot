package org.wanna.jabbot.binding.event;

import org.wanna.jabbot.binding.Binding;
import org.wanna.jabbot.binding.config.RoomConfiguration;

/**
 * Event to notify that a binding have been invited to a room
 *
 * @author Vincent Morsiani [vmorsiani@voxbone.com]
 * @since 2016-03-03
 */
public class RoomInviteEvent extends AbstractBindingEvent<RoomConfiguration>{
	public RoomInviteEvent(Binding binding, RoomConfiguration payload) {
		super(binding, payload);
	}
}
