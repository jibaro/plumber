package com.dianping.plumber.core;

import com.dianping.plumber.config.PlumberConfig;
import com.dianping.plumber.exception.PlumberPipeTimeoutException;
import com.dianping.plumber.utils.StringUtils;
import com.dianping.plumber.view.ViewRenderer;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * Author: liangjun.zhong
 * Date: 14-11-11
 * Time: PM10:29
 * To change this template use File | Settings | File Templates.
 */
public class PlumberPipeWorker extends PlumberWorker {

    private final PlumberPipe pipe;
    private final LinkedBlockingQueue<String> pipeRenderResultQueue;
    private final ResultReturnedFlag resultReturnedFlag;

    public PlumberPipeWorker(PlumberPipeDefinition definition,
                             Map<String, Object> paramsFromRequest,
                             Map<String, Object> paramsFromController,
                             PlumberPipe pipe,
                             LinkedBlockingQueue<String> pipeRenderResultQueue,
                             ResultReturnedFlag resultReturnedFlag) {
        super(definition, paramsFromRequest, paramsFromController);
        this.pipe = pipe;
        this.pipeRenderResultQueue = pipeRenderResultQueue;
        this.resultReturnedFlag = resultReturnedFlag;
    }

    @Override
    public void run() {
        String renderResult = PlumberGlobals.EMPTY_RENDER_RESULT;
        try {
            ResultType resultType = pipe.execute(paramsFromRequest, paramsFromController, modelForView);
            if ( resultType==ResultType.SUCCESS ) {
                String name = definition.getName();
                String viewSource = definition.getViewSource();
                ViewRenderer viewRenderer = PlumberWorkerDefinitionsRepo.getViewRenderer();
                String result = viewRenderer.render(name, viewSource, modelForView);
                if ( StringUtils.isNotEmpty(result) )
                    renderResult = result;
            }
        } catch (Exception e) {

            logger.error("pipe " + definition.getName() + " execute failure", e);

            if ( isDevEnv() ) {
                try {
                    String result = ExceptionUtils.getFullStackTrace(e);
                    if ( StringUtils.isNotEmpty(result) )
                        renderResult = result;
                } catch (Exception e1) {
                    logger.error("can not get the exception full stack trace", e1);
                }
            }

        } finally {

            if ( resultReturnedFlag.isReturned() ) {
                logger.error("can not return the pipe " + definition.getName() + "'s render result", new PlumberPipeTimeoutException());
                return;
            }

            try {
                boolean insertResult = pipeRenderResultQueue.offer(renderResult, PlumberConfig.getResponseTimeout(), TimeUnit.MILLISECONDS);
                if ( !insertResult ) {
                    logger.error("can not return the pipe " + definition.getName() + "'s render result", new PlumberPipeTimeoutException());
                }
            } catch (InterruptedException e) {
                logger.error("can not return the pipe " + definition.getName() + "'s render result", new PlumberPipeTimeoutException(e));
            }

        }
    }

}
