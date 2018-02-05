package cc.blynk.integration.tcp;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.server.api.http.AppAndHttpsServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.model.Profile;
import cc.blynk.server.core.model.device.Tag;
import cc.blynk.server.core.model.enums.PinType;
import cc.blynk.server.core.model.serialization.JsonParser;
import cc.blynk.server.core.model.widgets.OnePinWidget;
import cc.blynk.server.core.model.widgets.Widget;
import cc.blynk.server.core.model.widgets.controls.Button;
import cc.blynk.server.core.model.widgets.controls.Slider;
import cc.blynk.server.core.model.widgets.others.Player;
import cc.blynk.server.core.model.widgets.others.Video;
import cc.blynk.server.core.model.widgets.ui.Menu;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.hardware.HardwareServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static cc.blynk.server.core.protocol.enums.Response.ILLEGAL_COMMAND_BODY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 2/2/2015.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class SetPropertyTest extends IntegrationBase {

    private BaseServer appServer;
    private BaseServer hardwareServer;
    private ClientPair clientPair;


    @Before
    public void init() throws Exception {
        this.hardwareServer = new HardwareServer(holder).start();
        this.appServer = new AppAndHttpsServer(holder).start();
        this.clientPair = initAppAndHardPair("user_profile_json.txt");
    }

    @After
    public void shutdown() {
        this.appServer.close();
        this.hardwareServer.close();
        this.clientPair.stop();
    }

    @Test
    public void testSetWidgetProperty() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte)4, PinType.VIRTUAL);
        assertNotNull(widget);
        assertEquals("Some Text", widget.label);

        clientPair.hardwareClient.setProperty(4, "label", "MyNewLabel");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 4 label MyNewLabel")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        profile = parseProfile(clientPair.appClient.getBody());

        widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertNotNull(widget);
        assertEquals("MyNewLabel", widget.label);
    }

    @Test
    //https://github.com/blynkkk/blynk-server/issues/756
    public void testSetWidgetPropertyIsNotRestoredForTagWidgetAfterOverriding() throws Exception {
        Tag tag0 = new Tag(100_000, "Tag1", new int[] {0});

        clientPair.appClient.createTag(1, tag0);
        String createdTag = clientPair.appClient.getBody();
        Tag tag = JsonParser.parseTag(createdTag, 0);
        assertNotNull(tag);
        assertEquals(100_000, tag.id);
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(createTag(1, tag)));

        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody(2));

        Slider slider = (Slider) profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertNotNull(slider);
        slider.width = 2;
        slider.height = 2;
        assertEquals("Some Text", slider.label);
        slider.deviceId = tag0.id;

        clientPair.appClient.updateWidget(1, slider);
        clientPair.appClient.verifyResult(ok(3));

        clientPair.hardwareClient.setProperty(4, "label", "MyNewLabel");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 4 label MyNewLabel")));

        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(4));

        slider.label = "Some Text2";
        clientPair.appClient.updateWidget(1, slider);
        clientPair.appClient.verifyResult(ok(5));

        clientPair.appClient.activate(1);
        clientPair.appClient.verifyResult(ok(6));
        verify(clientPair.appClient.responseMock, after(500).never()).channelRead(any(), eq(setProperty(1111, "1-0 4 label MyNewLabel")));

    }

    @Test
    public void testSetButtonProperty() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"onLabel\":\"On\", \"offLabel\":\"Off\" , \"type\":\"BUTTON\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(17, "onLabel", "вкл");
        clientPair.hardwareClient.setProperty(17, "offLabel", "выкл");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(ok(2)));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 17 onLabel вкл")));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(2, "1-0 17 offLabel выкл")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 17, PinType.VIRTUAL);
        assertNotNull(widget);
        assertTrue(widget instanceof Button);
        Button button = (Button) widget;

        assertEquals("вкл", button.onLabel);
        assertEquals("выкл", button.offLabel);
    }


    @Test
    public void testSetBooleanProperty() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"PLAYER\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(17, "isOnPlay", "true");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 17 isOnPlay true")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 17, PinType.VIRTUAL);
        assertNotNull(widget);
        assertTrue(widget instanceof Player);
        Player playerWidget = (Player) widget;

        assertTrue(playerWidget.isOnPlay);
    }

    @Test
    public void testSetStringArrayWidgetPropertyForMenu() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"MENU\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(17, "labels", "label1 label2 label3");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 17 labels label1 label2 label3")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 17, PinType.VIRTUAL);
        assertNotNull(widget);
        assertTrue(widget instanceof Menu);
        Menu menuWidget = (Menu) widget;

        assertArrayEquals(new String[] {"label1", "label2", "label3"}, menuWidget.labels);
    }

    @Test
    public void testSetWrongWidgetProperty() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(widget.label, "Some Text");

        clientPair.hardwareClient.setProperty(4, "YYY", "MyNewLabel");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, ILLEGAL_COMMAND_BODY)));

        clientPair.appClient.send("loadProfileGzipped");
        profile = parseProfile(clientPair.appClient.getBody(2));
        widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);

        assertEquals(widget.label, "Some Text");
    }

    @Test
    public void testSetWrongWidgetProperty2() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(widget.x, 1);

        clientPair.hardwareClient.setProperty(4, "x", "0");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, ILLEGAL_COMMAND_BODY)));

        clientPair.appClient.send("loadProfileGzipped");
        profile = parseProfile(clientPair.appClient.getBody(2));
        widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);

        assertEquals(widget.x, 1);
    }

    @Test
    public void testSetWrongWidgetProperty3() throws Exception {
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(widget.x, 1);

        clientPair.hardwareClient.setProperty(4, "url", "0");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, ILLEGAL_COMMAND_BODY)));
    }

    @Test
    public void testSetColorForWidget() throws Exception {
        clientPair.hardwareClient.setProperty(4, "color", "#23C48E");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 4 color #23C48E")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());
        profile.dashBoards[0].updatedAt = 0;

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(600084223, widget.color);
    }

    @Test
    public void setMinMaxProperty() throws Exception {
        clientPair.hardwareClient.setProperty(4, "min", "10");
        clientPair.hardwareClient.setProperty(4, "max", "20");
        clientPair.hardwareClient.verifyResult(ok(1));
        clientPair.hardwareClient.verifyResult(ok(2));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 4 min 10")));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(2, "1-0 4 max 20")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());
        profile.dashBoards[0].updatedAt = 0;

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(10, ((OnePinWidget) widget).min);
        assertEquals(20, ((OnePinWidget) widget).max);
    }

    @Test
    public void setMinMaxPropertyFloat() throws Exception {
        clientPair.hardwareClient.setProperty(4, "min", "10.1");
        clientPair.hardwareClient.setProperty(4, "max", "20.2");
        clientPair.hardwareClient.verifyResult(ok(1));
        clientPair.hardwareClient.verifyResult(ok(2));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 4 min 10.1")));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(2, "1-0 4 max 20.2")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());
        profile.dashBoards[0].updatedAt = 0;

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 4, PinType.VIRTUAL);
        assertEquals(10, ((OnePinWidget) widget).min);
        assertEquals(20, ((OnePinWidget) widget).max);
    }

    @Test
    public void setMinMaxWrongPropertyFloat() throws Exception {
        clientPair.hardwareClient.setProperty(4, "min", "10.11-1");
        clientPair.hardwareClient.setProperty(4, "max", "20.22-2");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(illegalCommandBody(1)));
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(illegalCommandBody(2)));
        verify(clientPair.appClient.responseMock, after(50).never()).channelRead(any(), any());
        verify(clientPair.appClient.responseMock, after(50).never()).channelRead(any(), any());
    }

    @Test
    public void testSetColorShouldNotWorkForNonActiveProject() throws Exception {
        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(4, "color", "#23C48E");
        verify(clientPair.hardwareClient.responseMock, after(500).never()).channelRead(any(), eq(ok(1)));
        verify(clientPair.appClient.responseMock, after(500).never()).channelRead(any(), eq(setProperty(1, "1 4 color #23C48E")));
    }

    @Test
    public void testSetUrlForVideo() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"VIDEO\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(17, "url", "http://123.com");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 17 url http://123.com")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 17, PinType.VIRTUAL);
        assertNotNull(widget);
        assertTrue(widget instanceof Video);
        Video videoWidget = (Video) widget;

        assertEquals("http://123.com", videoWidget.url);
    }

    @Test
    public void testPropertyIsNotRestoredAfterWidgetCreated() throws Exception {
        clientPair.hardwareClient.setProperty(122, "label", "new");
        clientPair.hardwareClient.verifyResult(ok(1));

        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"VIDEO\", \"pinType\":\"VIRTUAL\", \"pin\":122}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.activate(1);
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(3)));
        verify(clientPair.appClient.responseMock, never()).channelRead(any(), eq(setProperty(1111, "1 122 label new")));
    }

    @Test
    public void testPropertyIsNotRestoredAfterWidgetUpdated() throws Exception {
        clientPair.appClient.createWidget(1, "{\"id\":102, \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"VIDEO\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(1));

        clientPair.hardwareClient.setProperty(17, "url", "http://123.com");
        clientPair.hardwareClient.verifyResult(ok(1));
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(setProperty(1, "1-0 17 url http://123.com")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        Profile profile = parseProfile(clientPair.appClient.getBody());

        Widget widget = profile.dashBoards[0].findWidgetByPin(0, (byte) 17, PinType.VIRTUAL);
        assertNotNull(widget);
        assertTrue(widget instanceof Video);
        Video videoWidget = (Video) widget;

        assertEquals("http://123.com", videoWidget.url);

        clientPair.appClient.updateWidget(1, "{\"id\":102, \"url\":\"http://updated.com\", \"width\":1, \"height\":1, \"x\":5, \"y\":0, \"tabId\":0, \"label\":\"Some Text\", \"type\":\"VIDEO\", \"pinType\":\"VIRTUAL\", \"pin\":17}");
        clientPair.appClient.verifyResult(ok(2));

        clientPair.appClient.deactivate(1);
        clientPair.appClient.verifyResult(ok(3));

        clientPair.appClient.activate(1);
        verify(clientPair.appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(4)));
        verify(clientPair.appClient.responseMock, never()).channelRead(any(), eq(setProperty(1111, "1 17 url http://updated.com")));

        clientPair.appClient.reset();
        clientPair.appClient.send("loadProfileGzipped");
        profile = parseProfile(clientPair.appClient.getBody());
        assertEquals(0, profile.dashBoards[0].pinsStorage.size());
    }
}
