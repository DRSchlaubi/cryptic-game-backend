package net.cryptic_game.backend.server.server.websocket.endpoints;

import static net.cryptic_game.backend.base.utils.ValidationUtils.checkMail;
import static net.cryptic_game.backend.base.utils.ValidationUtils.checkPassword;
import static net.cryptic_game.backend.server.server.websocket.WebSocketUtils.build;

import com.google.gson.JsonObject;
import java.util.UUID;
import net.cryptic_game.backend.base.api.ApiCollection;
import net.cryptic_game.backend.base.utils.JsonBuilder;
import net.cryptic_game.backend.base.utils.SecurityUtils;
import net.cryptic_game.backend.base.utils.ValidationUtils;
import net.cryptic_game.backend.data.user.User;
import net.cryptic_game.backend.data.user.UserWrapper;
import net.cryptic_game.backend.data.user.session.Session;
import net.cryptic_game.backend.data.user.session.SessionWrapper;
import net.cryptic_game.backend.server.client.Client;
import net.cryptic_game.backend.server.server.ServerResponseType;
import net.cryptic_game.backend.server.server.websocket.WebSocketUtils;

public class UserEndpoints extends ApiCollection {

    public JsonObject login(Client client,
                            String name,
                            String password,
                            String deviceName) {
        if (client.getUser() != null) return build(ServerResponseType.FORBIDDEN, "ALREADY_LOGGED_IN");

        User user = UserWrapper.getByName(name);

        if (user == null) return build(ServerResponseType.UNAUTHORIZED, "INVALID_NAME");
        if (!UserWrapper.verifyPassword(user, password))
            return build(ServerResponseType.UNAUTHORIZED, "INVALID_PASSWORD");

        UUID token = UUID.randomUUID();
        client.setSession(user, token, deviceName);

        return build(ServerResponseType.OK, JsonBuilder.anJSON()
                .add("session", client.getSession().getId())
                .add("token", token).build());
    }

    public JsonObject register(Client client,
                               String name,
                               String password,
                               String mail,
                               String deviceName) {
        if (client.getUser() != null) return build(ServerResponseType.FORBIDDEN, "ALREADY_LOGGED_IN");

        if (!checkPassword(password)) return build(ServerResponseType.BAD_REQUEST, "INVALID_PASSWORD");
        if (!checkMail(mail)) return build(ServerResponseType.BAD_REQUEST, "INVALID_MAIL");
        if (UserWrapper.getByName(name) != null) return build(ServerResponseType.FORBIDDEN, "USER_ALREADY_EXISTS");

        User user = UserWrapper.registerUser(name, mail, password);
        UUID token = UUID.randomUUID();
        client.setSession(user, token, deviceName);

        return build(ServerResponseType.OK, JsonBuilder.anJSON()
                .add("session", client.getSession().getId())
                .add("token", token).build());

    }

    public JsonObject session(Client client,
                              UUID sessionId,
                              UUID token) {
        if (client.getUser() != null) return build(ServerResponseType.FORBIDDEN, "ALREADY_LOGGED_IN");

        Session session = SessionWrapper.getSessionById(sessionId);
        if (session == null) return WebSocketUtils.build(ServerResponseType.NOT_FOUND, "INVALID_SESSION");
        if (!SecurityUtils.verify(token.toString(), session.getTokenHash()))
            return WebSocketUtils.build(ServerResponseType.UNAUTHORIZED, "INVALID_SESSION_TOKEN");
        if (!session.isValid()) return WebSocketUtils.build(ServerResponseType.UNAUTHORIZED, "SESSION_EXPIRED");

        client.setSession(session);

        return build(ServerResponseType.OK);
    }

    public JsonObject password(Client client,
                               String password,
                               String newPassword) {
        if (client.getUser() == null) return build(ServerResponseType.FORBIDDEN, "NOT_LOGGED_IN");

        if (!ValidationUtils.checkPassword(newPassword))
            return build(ServerResponseType.BAD_REQUEST, "INVALID_PASSWORD");
        if (!UserWrapper.verifyPassword(client.getUser(), password))
            return build(ServerResponseType.UNAUTHORIZED, "INVALID_PASSWORD");

        UserWrapper.setPassword(client.getUser(), newPassword);

        return build(ServerResponseType.OK);
    }

    public JsonObject logout(Client client,
                             UUID sessionId) {
        if (client.getUser() == null) return build(ServerResponseType.FORBIDDEN, "NOT_LOGGED_IN");

        if (sessionId == null) {
            client.logout();
            return build(ServerResponseType.OK);
        }

        Session session = SessionWrapper.getSessionById(sessionId);

        if (session == null) return build(ServerResponseType.NOT_FOUND, "SESSION_NOT_FOUND");

        if (client.getSession().equals(session))
            client.logout();
        else
            SessionWrapper.closeSession(session);

        return build(ServerResponseType.OK);
    }

    public JsonObject delete(Client client,
                             String password) {
        if (client.getUser() == null) return build(ServerResponseType.FORBIDDEN, "NOT_LOGGED_IN");

        if (!UserWrapper.verifyPassword(client.getUser(), password))
            return build(ServerResponseType.UNAUTHORIZED, "INVALID_PASSWORD");

        User user = client.getUser();
        client.logout();
        UserWrapper.deleteUser(user);

        return build(ServerResponseType.OK);
    }
}
