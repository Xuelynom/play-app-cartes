package controllers;

import static play.libs.F.Matcher.ClassOf;
import static play.libs.F.Matcher.Equals;
import static play.mvc.Http.WebSocketEvent.SocketClosed;
import static play.mvc.Http.WebSocketEvent.TextFrame;
import play.Logger;
import play.libs.F.Either;
import play.libs.F.EventStream;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Http.WebSocketClose;
import play.mvc.Http.WebSocketEvent;
import play.mvc.Scope.Session;
import play.mvc.WebSocketController;
import autre.ChatRoom;
import autre.ChatRoom.Message;

public class WebSocket extends Controller {

	public static void room(String user) {
		render(user);
	}

	public static class ChatRoomSocket extends WebSocketController {

		public static void join(String user) {

			// ChatRoom room = ChatRoom.get(new
			// Long(Session.current().get("partieId")));
			ChatRoom room = ChatRoom.get(Session.current().get("partieId"));

			// Socket connected, join the chat room
			EventStream<ChatRoom.Event> roomMessagesStream = room.join(user);

			// Loop while the socket is open
			while (inbound.isOpen()) {

				// Wait for an event (either something coming on the inbound socket
				// channel, or ChatRoom messages)
				Either<WebSocketEvent, ChatRoom.Event> e = await(Promise.waitEither(inbound.nextEvent(), roomMessagesStream.nextEvent()));
				Logger.info("*********************** evenement " + e._1);
				// Case: User typed 'quit'
				for (String userMessage : TextFrame.and(Equals("quit")).match(e._1)) {
					room.leave(user);
					outbound.send("quit:ok");
					disconnect();
				}

				// Case: TextEvent received on the socket
				for (String userMessage : TextFrame.match(e._1)) {
					room.publish(new Message(user, userMessage));
				}

				// Case: Someone joined the room
				for (ChatRoom.Join joined : ClassOf(ChatRoom.Join.class).match(e._2)) {
					outbound.send("join:%s", joined.user);
				}

				// Case: New message on the chat room
				for (ChatRoom.Message message : ClassOf(ChatRoom.Message.class).match(e._2)) {
					Logger.info("*********************** msg " + message.texte);
					outbound.send("message:%s:%s", message.user, message.texte);
				}

				// Case: Someone left the room
				for (ChatRoom.Leave left : ClassOf(ChatRoom.Leave.class).match(e._2)) {
					outbound.send("leave:%s", left.user);
				}

				// Case: Someone do action
				for (ChatRoom.ActionGenerale action : ClassOf(ChatRoom.ActionGenerale.class).match(e._2)) {
					outbound.send("action");
				}

				// Case: The socket has been closed
				for (WebSocketClose closed : SocketClosed.match(e._1)) {
					room.leave(user);
					disconnect();
				}

			}

		}

	}

}
