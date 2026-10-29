package com.battleship.frontend.pvp;

import com.battleship.shared.auth.AccountProfile;
import com.battleship.shared.game.PlayerIdentity;
import com.battleship.shared.protocol.*;
import com.battleship.shared.room.*;
import com.jme3.asset.DesktopAssetManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real TCP adapter + scripted responses, not a PostgreSQL/server business-rule test. */
public final class InviteNotificationChecks {
    public static void main(String[] args) throws Exception {
        String oldHost=System.getProperty("battleship.host"),oldPort=System.getProperty("battleship.port");
        try {
            System.setProperty("battleship.host","127.0.0.1");
            var assets=new DesktopAssetManager(true);
            for(boolean incoming:new boolean[]{false,true}) for(boolean accept:new boolean[]{false,true}) check(assets,incoming,accept);
        } finally { restore("battleship.host",oldHost); restore("battleship.port",oldPort); }
        System.out.println("PASS invitation notifications: sender/recipient ACCEPTED and DECLINED auto-close, second invitation works; real TCP with scripted server.");
    }
    private static void check(DesktopAssetManager assets,boolean incoming,boolean accept) throws Exception {
        var own=new AccountProfile(UUID.randomUUID(),"invite_check","Admiral Tester");
        var other=new AccountProfile(UUID.randomUUID(),"other_admiral","Admiral Opponent");
        var terminal=accept?GameInviteStatus.ACCEPTED:GameInviteStatus.DECLINED;
        var readyToFinish=new CountDownLatch(1);
        try(var listener=new ServerSocket(0);var worker=Executors.newSingleThreadExecutor()) {
            System.setProperty("battleship.port",Integer.toString(listener.getLocalPort()));
            Future<?> server=worker.submit(()->{
                try(Socket socket=listener.accept()) {
                    socket.setSoTimeout(5000);
                    var input=new DataInputStream(socket.getInputStream());var output=new DataOutputStream(socket.getOutputStream());
                    UUID id=UUID.randomUUID(); Message request;
                    while((request=MessageCodec.read(input))!=null) {
                        switch(request.type()) {
                            case CONNECT -> MessageCodec.write(output,Message.reply(MessageType.CONNECT_OK,request.requestId(),new ConnectResponse(new PlayerIdentity("invite-client",own.displayName()))));
                            case LOGIN -> {
                                MessageCodec.write(output,Message.reply(MessageType.AUTH_SUCCESS,request.requestId(),own));
                                if(incoming) publish(output,UUID.randomUUID().toString(),id,other,own,GameInviteStatus.PENDING);
                            }
                            case SEND_GAME_INVITE -> {
                                id=UUID.randomUUID(); publish(output,request.requestId(),id,own,other,GameInviteStatus.PENDING);
                                if(!readyToFinish.await(4,TimeUnit.SECONDS)) throw new AssertionError("Pending notification was not observed");
                                publish(output,UUID.randomUUID().toString(),id,own,other,terminal);
                            }
                            case ACCEPT_GAME_INVITE,DECLINE_GAME_INVITE -> {
                                require(request.payloadAs(GameInviteResponse.class).inviteId().equals(id),"Answer uses correct invitation ID");
                                publish(output,request.requestId(),id,other,own,terminal);
                            }
                            case PING -> MessageCodec.write(output,Message.reply(MessageType.PONG,request.requestId(),null));
                            case DISCONNECT -> { return; }
                            default -> throw new AssertionError("Unexpected request "+request.type());
                        }
                    }
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            try(var client=new PvpController(new PvpRoomPanel(assets),own.displayName(),r->{},()->{},()->{},m->{},e->{})) {
                var panel=new GameInvitePanel(assets);panel.resize(1280,800);
                var controller=new GameInviteController(client,panel);
                client.openAccount();await(client,controller,client::connected);
                client.login(own.username(),"TransportOnlyPassword!");await(client,controller,()->client.account()!=null);client.open();
                if(!incoming) controller.send(other.id());
                await(client,controller,()->controller.current()!=null&&controller.current().status()==GameInviteStatus.PENDING&&panel.visible()&&!controller.busy());
                var action=incoming?(accept?GameInvitePanel.Action.ACCEPT:GameInvitePanel.Action.DECLINE):GameInvitePanel.Action.DISMISS;
                for(int[] size:new int[][]{{960,600},{1280,800},{1920,1080}}) {
                    panel.resize(size[0],size[1]);
                    require(panel.actionAt(panel.buttonCenter(action)).orElseThrow()==action,"Compact notification hit target scales");
                }
                if(incoming) controller.handle(action); else readyToFinish.countDown();
                await(client,controller,()->controller.current()!=null&&controller.current().status()==terminal&&!controller.busy());
                require(!panel.visible(),"Resolved invitation automatically closes for "+(incoming?"recipient":"sender")+" / "+terminal);
                for(int i=0;i<4;i++){client.update();controller.update();require(!panel.visible(),"Resolved notification stays closed");}
                if(!incoming) {
                    UUID previous=controller.current().inviteId(); controller.send(other.id());
                    require(panel.visible(),"Sending again shows pending request feedback");
                    await(client,controller,()->controller.current()!=null&&!controller.current().inviteId().equals(previous)&&controller.current().status()==terminal);
                    require(!panel.visible(),"Second response cannot reopen a stale resolved notification");
                }
            }
            server.get(5,TimeUnit.SECONDS);
        }
    }
    private static void publish(DataOutputStream output,String request,UUID id,AccountProfile from,AccountProfile to,GameInviteStatus status) throws IOException {
        MessageCodec.write(output,Message.reply(MessageType.GAME_INVITE_UPDATED,request,
                new GameInvite(id,"ABC234",from,to,status,status==GameInviteStatus.PENDING?60_000:0,"PvP invitation "+status.name().toLowerCase(Locale.ROOT))));
    }
    private static void await(PvpController client,GameInviteController controller,BooleanSupplier done) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!done.getAsBoolean()&&System.nanoTime()<until){client.update();controller.update();Thread.sleep(5);}
        require(done.getAsBoolean(),"Timed out waiting for invitation state");
    }
    private static void restore(String key,String value){if(value==null)System.clearProperty(key);else System.setProperty(key,value);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
