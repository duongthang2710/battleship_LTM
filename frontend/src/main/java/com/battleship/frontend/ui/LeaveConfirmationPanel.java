package com.battleship.frontend.ui;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial.CullHint;

public final class LeaveConfirmationPanel {
    private final Node root=new Node("Leave confirmation"),card=new Node("Leave confirmation card");
    private final AssetManager assets;
    private float scale=1,left,bottom;
    private Runnable confirmed;
    public LeaveConfirmationPanel(AssetManager assets) { this.assets=assets; root.attachChild(card);cancel(); }
    public Node root() { return root; }
    public boolean visible() { return confirmed!=null; }
    public void resize(int width,int height) {
        scale=UiTypography.scale(width,height);left=(width-440*scale)/2;bottom=(height-210*scale)/2;
        card.setLocalScale(scale);card.setLocalTranslation(left,bottom,0);
    }
    public void show(Runnable action) {
        confirmed=action;root.setCullHint(CullHint.Never);card.detachAllChildren();
        var draw=new NavalHudSkin(assets,card,400);
        draw.panel(0,0,440,210,0x081F32);
        draw.text("LEAVE ROOM?",30,20,130,400,52,NavalHudSkin.WHITE);
        draw.text("Leaving an active battle counts as a defeat.",16,24,94,392,28,0xC0D4DC);
        draw.panel(24,24,186,46,0x7E2531); draw.text("LEAVE",19,24,24,186,46,NavalHudSkin.WHITE);
        draw.panel(230,24,186,46,0x143B54); draw.text("CANCEL",19,230,24,186,46,NavalHudSkin.WHITE);
    }
    public void cancel() { confirmed=null;root.setCullHint(CullHint.Always); }
    public void confirm() { Runnable action=confirmed;cancel();if(action!=null) action.run(); }
    public void click(Vector2f point) {
        float x=(point.x-left)/scale,y=(point.y-bottom)/scale;
        if(y<24||y>70) return;
        if(x>=24&&x<=210) confirm(); else if(x>=230&&x<=416) cancel();
    }
    public Vector2f buttonCenter(boolean leave) { return new Vector2f(left+(leave?117:323)*scale,bottom+47*scale); }
}
