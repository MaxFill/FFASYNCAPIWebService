package ru.rt.services;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import ru.rt.dict.Params;
import ru.rt.utils.SOAPLogger;
import javax.ejb.EJB;
import javax.jws.WebService;
import java.util.logging.Level;
import java.util.stream.Collectors;
import ru.rt.oms.Attribute;
import ru.rt.oms.Attributes;
import ru.rt.oms.Fault;
import ru.rt.oms.NotificationOrderItems;
import ru.rt.oms.NotificationResponse;
import ru.rt.oms.OrderParties;
import ru.rt.oms.OrderStatus;
import ru.rt.oms.OrderStatusNotification;
import ru.rt.oms.Party;
import ru.rt.oms.Result;

/**
 * Web служба получает асинхронные сообщения от Hermes и транслирует их в InstantLink
 *
 * @author Maksim.Filatov
 */
@WebService(serviceName = "OMSOrderNotificationWebService", 
    portName = "OMSOrderNotificationWebServicePort", 
    endpointInterface = "ru.rt.oms.OMSOrderNotificationWebService", targetNamespace = "http://oms.rt.ru/", wsdlLocation = "WEB-INF/wsdl/RTFFASYNCAPI_v01.wsdl")
public class OMSOrderNotificationWebService {
    private static final String SUCCES_RESULT_CODE = "0";
    private static final String SUCCES_RESULT_TEXT = "OK. The request was sent successfully to the InstantLink";
    private static final String FAIL_RESULT_CODE = "1"; 
    
    @EJB private RmiFifoClient rmiFifoClient;
    @EJB private SOAPLogger logger;

    public NotificationResponse notifyOrderStatus(OrderStatusNotification request) throws Fault {
	OrderStatusNotification notification = sanitizer(request);
        final String orderId = notification.getOrder().getOrderId();	
	final String logInfo = logger.makeLogInfo(notification, orderId);
	final String omsId = notification.getOrder().getOrderOMSId();        
	NotificationResponse response = new NotificationResponse();
        response.setOriginator(notification.getReceiver());
        response.setReceiver(notification.getOriginator());	
	Set<String> errors = new HashSet<>();
	try {	    
	    rmiFifoClient.sendNotifToIL(notification, logInfo, errors);	  //отправка запроса (нотификации) в IL  
	    if (errors.isEmpty()){
		response.setResult(getSuccesResult());
	    } else {
		response.setResult(getFailResult(errors));
	    }
	} catch (InterruptedException ex) {
	    Params.LOGGER.log(Level.SEVERE, null, ex);
	    errors.add(ex.getMessage());
	    response.setResult(getFailResult(errors));
	}
	logger.saveResponseToFile(response, orderId, notification.getRequestId(), omsId);
	//Params.LOGGER.log(Level.INFO, "", new Object[]{logInfo});
	return response;
    }

    /* *** privates *** */
    
    private OrderStatusNotification sanitizer(OrderStatusNotification request){
	checkNotification(request);
	return request;
    }
    
    private Result getSuccesResult() {
        Result result = new Result();
	result.setResultCode(SUCCES_RESULT_CODE);
	result.setResultText(SUCCES_RESULT_TEXT);
        return result;
    }
    
    private Result getFailResult(Set<String> errors) {
        String err = errors.stream().collect(Collectors.joining(", "));
	Result result = new Result();
	result.setResultCode(FAIL_RESULT_CODE);
	result.setResultText(err);
        return result;
    }
    
    /**
     * Очищает список equipment от null
     * @param notification 
     */
    private OrderStatusNotification checkNotification(OrderStatusNotification notification){	
	OrderStatus orderStatus = notification.getOrder();
	if (orderStatus != null){;
	    checkAttributes(orderStatus.getOrderAttributes());
	    checkNotificationOrderItems(orderStatus.getOrderItems());
	    checkParties(orderStatus.getOrderParties());
	}
	return notification;
    }
    
    private void checkNotificationOrderItems(NotificationOrderItems noi){
	if (noi == null) return;
	noi.getOrderItem().forEach(oi->checkParties(oi.getOrderItemParties()));
    }
    
    private void checkParties(OrderParties orderParties){
	if (orderParties == null) return;
	orderParties.getOrderPartyOrOrderAttachment()
	    .forEach(ob->{
		if (ob instanceof Party){
		    Party party = (Party) ob;		
		    checkAttributes(party.getPartyAttributes());
		}
	    });	
    }
    
    private void checkAttributes(Attributes attributes){
	if (attributes == null) return;
	attributes.getAttribute().forEach(atr->checkAttribute(atr));
    }
    
    private void checkAttribute(Attribute attribute){
	if (attribute == null) return;
	if ("equipmentList".equalsIgnoreCase(attribute.getName().trim())){
	    List<Object> clearEquip = clearNullEquipment(attribute.getContent());
	    attribute.getContent().clear();
	    attribute.getContent().addAll(clearEquip);
	}
    }
    
    private List<Object> clearNullEquipment(List<Object> obj){
	obj.removeIf(Objects::isNull);
	return obj.stream().filter(o -> {
	    if (o instanceof String){
		String e = ((String) o).trim();
		if ("[equipment: null]".equals(e)){
		    return false;
		}
	    }
	    return true;
	})
	.collect(Collectors.toList());
    }

}