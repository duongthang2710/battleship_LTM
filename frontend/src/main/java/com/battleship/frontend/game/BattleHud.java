package com.battleship.frontend.game;

import com.battleship.frontend.ui.NavalHudSkin;
import com.battleship.frontend.ui.BattleSidebarLayout;
import com.battleship.shared.game.*;
import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapText;
import com.jme3.math.*;
import com.jme3.scene.*;
import static com.battleship.frontend.ui.NavalHudSkin.*;

public final class BattleHud {
    private final Node root=new Node("Battle HUD"), layout=new Node("Commander HUD layout");
    private final Node fleetList=new Node("Enemy fleet status"), feedback=new Node("Commander shot feedback");
    private final Node intro=new Node("Match countdown");
    private String introText="";
    private final AssetManager assets;
    private NavalHudSkin skin;
    private String playerName, opponentName="Opponent", statusTitle="Deploy Fleet", detail="", turnTitle="YOUR TURN";
    private Side side=Side.WHITE;
    private GamePhase phase=GamePhase.PLACEMENT;
    private GameRules rules=GameRules.DEFAULT;
    private int sunkMask;
    private float scale=1, w=1280, h=800, left, flagWidth, ownX, ownY, rivalX, rivalY;
    private Geometry ownArc, rivalArc;
    private BitmapText header, caption;
    private boolean clockVisible, ownTurn=true;
    private long remaining=60_000, drawnRemaining=-1;
    private String shotResult="";
    private boolean playerFired;
    private boolean opponentReady;
    public void setOpponentReady(boolean ready) { if(opponentReady!=ready) { opponentReady=ready; rebuild(); } }
    public BattleHud(AssetManager assets,String playerName) {
        this.assets=assets; this.playerName=playerName; root.attachChild(layout);
    }
    public Node root() { return root; }
    public void resize(int width,int height) {
        scale=Math.min(width/1280f,height/800f); w=width/scale; h=height/scale; rebuild();
    }
    public void setBattleState(GameState state,GameRules rules) {
        int mask=0;
        for(Ship ship:state.enemy().board().ships()) if(ship.isSunk()) mask|=1<<ship.type().ordinal();
        GamePhase next=state.phase()==GamePhase.PLACEMENT?GamePhase.PLACEMENT:GamePhase.BATTLE;
        boolean layoutChanged=phase!=next; boolean fleetChanged=!rules.equals(this.rules)||mask!=sunkMask;
        phase=next; this.rules=rules; sunkMask=mask;
        if(layoutChanged) rebuild(); else if(fleetChanged) rebuildFleet();
    }
    private void rebuild() {
        layout.detachAllChildren(); layout.setLocalScale(scale); header=null; caption=null;
        skin=new NavalHudSkin(assets,layout,10);
        left=BattleSidebarLayout.flagLeft(w); flagWidth=BattleSidebarLayout.flagWidth(w);
        ownX=left+flagWidth/2; ownY=h-142; rivalX=w-116; rivalY=132;
        skin.banner(left,phase==GamePhase.PLACEMENT?70:h-BattleSidebarLayout.BANNER_HEIGHT,flagWidth,h);
        skin.text(playerName,22,left+6,h-50,flagWidth-12,35,WHITE);
        skin.line(left+6,h-65,left+flagWidth-6,h-65,WHITE,1);
        avatar(ownX,ownY,74,side!=Side.BLACK,true);
        avatar(rivalX,rivalY,62,side==Side.BLACK,false);
        skin.panel(rivalX-88,30,176,33,ORANGE);
        skin.text(opponentName,16,rivalX-84,31,168,31,WHITE);
        if(phase==GamePhase.PLACEMENT) {
            skin.panel(rivalX-88,67,176,24,0x092B43);
            skin.text(opponentReady?"READY!":"DEPLOYING",18,rivalX-84,67,168,24,WHITE);
            if(opponentReady) {
                skin.line(rivalX+24,rivalY-8,rivalX+40,rivalY-22,0x6EE448,7);
                skin.line(rivalX+40,rivalY-22,rivalX+74,rivalY+17,0x6EE448,7);
            }
            if (!statusTitle.equals("Fleet confirmed")) {
                skin.panel(w*.30f,h-60,w*.40f,32,BLUE);
                header=skin.text("DEPLOY FLEET",22,w*.30f,h-60,w*.40f,32,WHITE);
            }
        } else {
            caption=skin.text(detail,15,w*.29f,76,w*.45f,24,WHITE);
        }
        layout.attachChild(fleetList); layout.attachChild(feedback); layout.attachChild(intro);
        rebuildFleet(); redrawFeedback(); redrawIntro(); drawnRemaining=-1; drawClock();
    }
    private void avatar(float x,float y,float radius,boolean radiant,boolean own) {
        Node commander=new Node(own?"Own commander foreground":"Enemy commander foreground");
        commander.setLocalTranslation(0,0,6); layout.attachChild(commander);
        var portraitSkin=new NavalHudSkin(assets,commander,10);
        // Opaque backing prevents the fleet pennant showing through portrait alpha edges.
        if(!own) new NavalHudSkin(assets,commander,7)
                .arc("Enemy portrait underlay",x,y,(radius-1)/2,radius-1,0,FastMath.TWO_PI,0x092B43);
        portraitSkin.arc("Portrait rim",x,y,radius,3,0,FastMath.TWO_PI,WHITE);
        Geometry portrait=portraitSkin.portrait("textures/portraits/"+(radiant?"radiant-admiral.png":"abyssal-admiral.png"),x,y,radius-3);
        portrait.setName(own?"Own commander portrait":"Enemy commander portrait");
        portrait.setUserData("commanderRole",own?"self":"opponent");
        portraitSkin.arc("Timer segment track",x,y,radius+3,10,FastMath.PI,FastMath.PI,0xD7E2DE)
                .setMesh(NavalHudSkin.arcMesh(radius+3,10,FastMath.PI,FastMath.PI,true));
        Geometry arc=portraitSkin.arc(own?"Player turn arc":"Opponent turn arc",x,y,radius+3,7,FastMath.PI,FastMath.PI,own?0x21BDE5:ORANGE);
        arc.move(0,0,.3f);
        if(own) ownArc=arc; else rivalArc=arc;
    }
    private void rebuildFleet() {
        fleetList.detachAllChildren(); if(phase==GamePhase.PLACEMENT) return;
        var draw=new NavalHudSkin(assets,fleetList,13);
        float top=h-185, bottom=rivalY+45, halfWidth=36, pitch=9;
        float spacing=Math.min(80,(top-230)/rules.shipCount());
        // The backing joins behind the opponent portrait; it must not wash over the face.
        var backing=new NavalHudSkin(assets,fleetList,8);
        backing.hazePennant(rivalX-halfWidth,bottom,halfWidth*2,top+55,25,.09f);
        backing.line(rivalX-halfWidth,bottom,rivalX-halfWidth,top+55,0xE1EBEF,1.8f);
        backing.line(rivalX+halfWidth,bottom,rivalX+halfWidth,top+55,0xE1EBEF,1.8f);
        backing.line(rivalX-halfWidth,top+55,rivalX,top+80,0xE1EBEF,1.8f);
        backing.line(rivalX,top+80,rivalX+halfWidth,top+55,0xE1EBEF,1.8f);
        fleetList.setUserData("fleetOwner","opponent");
        for(int i=0;i<rules.shipCount();i++) {
            ShipType type=rules.shipTypes().get(i); boolean sunk=(sunkMask&(1<<type.ordinal()))!=0;
            float y=top-i*spacing;
            for(int cell=0;cell<type.length();cell++) {
                float cellY=y-cell*pitch;
                Geometry border=draw.rect(rivalX-6.1f,cellY-.6f,12.2f,9.2f,WHITE,.94f);
                border.setName("Enemy ship cell border");
                Geometry fill=draw.rect(rivalX-5.5f,cellY,11,8,sunk?0x70545A:ORANGE,1);
                fill.setName("Enemy ship cell"); fill.setUserData("shipType",type.name()); fill.move(0,0,.1f);
            }
            if(sunk) {
                draw.line(rivalX-14,y+6,rivalX+14,y-(type.length()-1)*pitch-2,0xE4A396,2);
                draw.line(rivalX+14,y+6,rivalX-14,y-(type.length()-1)*pitch-2,0xE4A396,2);
            }
        }
    }
    public void setShotResult(String result,boolean playerFired) {
        if(result.equals(shotResult)&&playerFired==this.playerFired) return;
        shotResult=result; this.playerFired=playerFired; redrawFeedback();
    }
    public void clearShotResult() { if(!shotResult.isEmpty()) { shotResult=""; redrawFeedback(); } }
    public void setStartCountdown(long millis,boolean start) {
        String next=millis>0?Long.toString(Math.min(3,(millis+999)/1000)):start?"START":"";
        if(next.equals(introText)) return;
        introText=next; redrawIntro();
    }
    private void redrawIntro() {
        intro.detachAllChildren(); intro.setUserData("countdownValue",introText);
        if(introText.isEmpty()) return;
        var draw=new NavalHudSkin(assets,intro,80);
        draw.panel(w/2-100,h/2-88,200,176,0x092D47);
        draw.text(introText,introText.equals("START")?55:110,w/2-94,h/2-80,188,160,WHITE);
    }
    private void redrawFeedback() {
        feedback.detachAllChildren(); if(shotResult.isBlank()) return;
        var draw=new NavalHudSkin(assets,feedback,19);
        float x=playerFired?ownX:rivalX, y=playerFired?ownY-48:rivalY-36;
        boolean miss=shotResult.equals("MISS");
        draw.panel(x-86,y-22,172,44,miss?0x45637B:0x9D2824);
        draw.text(shotResult,28,x-82,y-22,164,44,WHITE);
    }
    public void hideTurnClock() {
        if(!clockVisible && drawnRemaining>=0) return;
        clockVisible=false; remaining=60_000; drawnRemaining=-1; drawClock();
    }
    public void setTurnClock(String player,long millis) { setTurnClock(player.equals(playerName),millis); }
    public void setTurnClock(boolean ownTurn,long millis) {
        boolean changed=!clockVisible||this.ownTurn!=ownTurn;
        clockVisible=true; this.ownTurn=ownTurn; remaining=Math.max(0,Math.min(60_000,millis));
        if(changed) drawnRemaining=-1; drawClock();
    }
    private void drawClock() {
        if(ownArc==null||drawnRemaining>=0&&Math.abs(remaining-drawnRemaining)<50) return;
        drawnRemaining=remaining;
        for(boolean own:new boolean[]{true,false}) {
            Geometry arc=own?ownArc:rivalArc; float radius=own?77:65;
            float fraction=!clockVisible?(phase==GamePhase.PLACEMENT?1:0):own==ownTurn?remaining/60_000f:0;
            arc.setMesh(NavalHudSkin.arcMesh(radius,7,FastMath.PI,FastMath.PI*fraction,true));
            arc.setUserData("remainingMillis",own==ownTurn&&clockVisible?remaining:0L);
            arc.setUserData("active",clockVisible&&own==ownTurn);
        }
    }
    public boolean contains(Vector2f point) {
        float x=point.x/scale,y=point.y/scale;
        return x>=left&&x<=left+flagWidth&&y>=(phase==GamePhase.PLACEMENT?70:h-BattleSidebarLayout.BANNER_HEIGHT)
                || x>=rivalX-92&&x<=rivalX+92&&y<=rivalY+72&&y>=25
                || phase!=GamePhase.PLACEMENT&&x>=rivalX-39&&x<=rivalX+39&&y>=rivalY+45&&y<=h-105
                || phase==GamePhase.PLACEMENT&&!statusTitle.equals("Fleet confirmed")
                    &&y>=h-62&&y<=h-25&&x>=w*.30f&&x<=w*.70f;
    }
    public void setPlayerSide(Side side) { if(this.side!=side){this.side=side;rebuild();} }
    public void setPlayerName(String name) { if(!name.equals(playerName)){playerName=name;rebuild();} }
    public void setOpponentName(String name) { if(!name.equals(opponentName)){opponentName=name;rebuild();} }
    public void setGameStatus(String title,String detail) {
        if(title.equals(statusTitle)&&detail.equals(this.detail)) return;
        boolean waitingChanged=statusTitle.equals("Fleet confirmed")!=title.equals("Fleet confirmed");
        statusTitle=title; this.detail=detail;
        if(phase==GamePhase.PLACEMENT && waitingChanged) { rebuild(); return; }
        if(title.startsWith("Your turn")||title.startsWith("Radiant turn")) turnTitle="YOUR TURN";
        else if(title.startsWith("Opponent turn")||title.startsWith("Abyssal turn")) turnTitle="ENEMY TURN";
        if(header!=null) {
            header.setText("DEPLOY FLEET");
            com.battleship.frontend.ui.UiTypography.centerIn(header,phase==GamePhase.PLACEMENT?w*.30f:w*.42f,h-60,
                    phase==GamePhase.PLACEMENT?w*.40f:w*.37f,phase==GamePhase.PLACEMENT?32:30);
        }
        if(caption!=null){ caption.setText(detail); com.battleship.frontend.ui.UiTypography.centerIn(caption,w*.29f,76,w*.45f,24); }
    }
}
