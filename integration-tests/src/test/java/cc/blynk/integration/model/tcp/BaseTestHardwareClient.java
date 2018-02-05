package cc.blynk.integration.model.tcp;

import cc.blynk.client.core.BaseClient;
import cc.blynk.integration.model.SimpleClientHandler;
import org.mockito.Mockito;

import java.util.Random;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 05.02.18.
 */
public abstract class BaseTestHardwareClient extends BaseClient {

    public final SimpleClientHandler responseMock = Mockito.mock(SimpleClientHandler.class);

    public BaseTestHardwareClient(String host, int port, Random messageIdGenerator) {
        super(host, port, messageIdGenerator);
    }

    public void never(Object exceptedResult) throws Exception {
        verify(responseMock, Mockito.never()).channelRead(any(), eq(exceptedResult));
    }

    public void verifyResult(Object exceptedResult, int times) throws Exception {
        verify(responseMock, timeout(500).times(times)).channelRead(any(), eq(exceptedResult));
    }

    public void verifyResult(Object exceptedResult) throws Exception {
        verifyResult(exceptedResult, 1);
    }

}
