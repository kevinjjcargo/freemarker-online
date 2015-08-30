package com.kenshoo.freemarker.resources;

import com.kenshoo.freemarker.model.FreeMarkerError;
import com.kenshoo.freemarker.model.FreeMarkerErrorReponse;
import com.kenshoo.freemarker.model.FreeMarkerPayload;
import com.kenshoo.freemarker.model.FreeMarkerResponse;
import com.kenshoo.freemarker.services.FreeMarkerService;
import com.kenshoo.freemarker.services.FreeMarkerServiceResponse;
import com.kenshoo.freemarker.util.DataModelParser;
import com.kenshoo.freemarker.util.DataModelParsingException;
import com.kenshoo.freemarker.util.ExceptionUtils;
import com.kenshoo.freemarker.view.FreeMarkerOnlineView;
import com.kenshoo.freemarker.view.FreeMarkerOnlineViewResultType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Created by pradeep on 8/28/2015.
 */
@Path("/api/execute")
@Component

public class FreeMarkerOnlineExecuteResource {
    private static final int MAX_TEMPLATE_INPUT_LENGTH = 10000;

    private static final int MAX_DATA_MODEL_INPUT_LENGTH = 10000;

    private static final String MAX_TEMPLATE_INPUT_LENGTH_EXCEEDED_ERROR_MESSAGE
            = "The template length has exceeded the {0} character limit set for this service.";

    private static final String MAX_DATA_MODEL_INPUT_LENGTH_EXCEEDED_ERROR_MESSAGE
            = "The data model length has exceeded the {0} character limit set for this service.";

    private static final String SERVICE_OVERBURDEN_ERROR_MESSAGE
            = "Sorry, the service is overburden and couldn't handle your request now. Try again later.";

    static final String DATA_MODEL_ERROR_MESSAGE_HEADING = "Failed to parse data model:";
    static final String DATA_MODEL_ERROR_MESSAGE_FOOTER = "Note: This is NOT a FreeMarker error message. "
            + "The data model syntax is specific to this online service.";

    private String result = null;
    private String error = null;
    @Autowired
    private FreeMarkerService freeMarkerService;
    private FreeMarkerResponse freeMarkerResponse;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response formResult(
            FreeMarkerPayload payload) {
        Map<String, String> problems = new HashMap<String, String>();
        freeMarkerResponse = new FreeMarkerResponse();
        if (StringUtils.isBlank(payload.getTemplate()) && StringUtils.isBlank(payload.getDataModel())) {
            return Response.status(400).entity("Empty Template & data").build();
        }

        if (payload.getDataModel().length() > MAX_DATA_MODEL_INPUT_LENGTH) {
            error = new MessageFormat(MAX_DATA_MODEL_INPUT_LENGTH_EXCEEDED_ERROR_MESSAGE, Locale.US)
                    .format(new Object[] { MAX_DATA_MODEL_INPUT_LENGTH });
            problems.put("DataModel", error);
            freeMarkerResponse.setProblems(problems);
            return buildFreeMarkerResponse(true);
        }
        Map<String, Object> dataModel;
        try {
            dataModel = DataModelParser.parse(payload.getDataModel(), freeMarkerService.getFreeMarkerTimeZone());
        } catch (DataModelParsingException e) {
            error = e.getMessage();
            problems.put("DataModel", decorateResultText(error));
            freeMarkerResponse.setProblems(problems);
            return buildFreeMarkerResponse(true);
        }

        if (payload.getTemplate().length() > MAX_TEMPLATE_INPUT_LENGTH) {
            error = new MessageFormat(MAX_TEMPLATE_INPUT_LENGTH_EXCEEDED_ERROR_MESSAGE, Locale.US)
                            .format(new Object[] { MAX_TEMPLATE_INPUT_LENGTH });
            problems.put("Template", error);
            freeMarkerResponse.setProblems(problems);
            return buildFreeMarkerResponse(true);
        }
        FreeMarkerServiceResponse freeMarkerServiceResponse;
        try {
            freeMarkerServiceResponse = freeMarkerService.calculateTemplateOutput(payload.getTemplate(), dataModel);
        } catch (RejectedExecutionException e) {
            error = SERVICE_OVERBURDEN_ERROR_MESSAGE;
            return Response.serverError().entity(new FreeMarkerErrorReponse(FreeMarkerError.FREEMARKER_SERVICE_TIMEOUT, error)).build();
        }
        if (freeMarkerServiceResponse.isSuccesful()){
            result = freeMarkerServiceResponse.getTemplateOutput();
            freeMarkerResponse.setResult(result);
            freeMarkerResponse.setTruncatedResult(freeMarkerServiceResponse.isTemplateOutputTruncated());
            return buildFreeMarkerResponse(false);
        } else {
            Throwable failureReason = freeMarkerServiceResponse.getFailureReason();
            error = ExceptionUtils.getMessageWithCauses(failureReason);
            problems.put("DataModel", error);
            freeMarkerResponse.setProblems(problems);
            return buildFreeMarkerResponse(true);
        }

    }
    private Response buildFreeMarkerResponse(boolean isError){
        Response.ResponseBuilder response = isError ? Response.serverError() : Response.ok();
        return response.entity(freeMarkerResponse).build();
    }
    private String decorateResultText(String resultText) {
        return DATA_MODEL_ERROR_MESSAGE_HEADING + "\n\n" + resultText + "\n\n" + DATA_MODEL_ERROR_MESSAGE_FOOTER;
    }
}
