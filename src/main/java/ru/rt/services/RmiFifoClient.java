package ru.rt.services;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import ru.rt.notify.NotifyPersist;
import ru.rt.oms.Attribute;
import ru.rt.oms.Attributes;
import ru.rt.oms.Fault;
import ru.rt.oms.NotificationOrderItems;
import ru.rt.oms.OrderParties;
import ru.rt.oms.OrderStatus;
import ru.rt.oms.OrderStatusNotification;
import ru.rt.oms.Party;

/**
 * Клиент RMI FIFO InstantLink
 * @author Maksim.Filatov
 */
@LocalBean
@Stateless
public class RmiFifoClient {        
    
    @EJB private NotifyPersist notifyPersist;
    
    /**
     * Connect to TaskEngine and change task satatus
     * @param notification
     * @param logInfo
     * @param errors
     * @throws Fault 
     * @throws java.lang.InterruptedException 
     */
    public void sendNotifToIL(OrderStatusNotification notification, final String logInfo, Set<String> errors) throws Fault, InterruptedException{		
	OrderStatus orderStatus = notification.getOrder();
	if (orderStatus == null){
	    throw new IllegalArgumentException("HERMES_TO_IL: OrderStatusNotification is NULL!");
	}
	
	checkOrderStatus(orderStatus); //очистка в checkOrderStatus объектов equipment от null элементов
	
	final String omsId = orderStatus.getOrderOMSId();
	if (omsId == null){
	    throw new IllegalArgumentException("HERMES_TO_IL: OrderStatusNotification.OrderStatus.OmsId is NULL!");
	}
	
	//Заглушка! Нотификации отправляет теперь другой сервис	
	notifyPersist.saveNotify(notification, logInfo, errors);		
    }

    /* *** privates *** */           
    
    private void checkOrderStatus(OrderStatus orderStatus){
	checkAttributes(orderStatus.getOrderAttributes());
	checkNotificationOrderItems(orderStatus.getOrderItems());
	checkParties(orderStatus.getOrderParties());
    }
    
    private void checkNotificationOrderItems(NotificationOrderItems noi){
	if (noi == null) return;
	noi.getOrderItem().forEach(oi->checkParties(oi.getOrderItemParties()));
    }
    
    private void checkParties(OrderParties orderParties){
	if (orderParties == null) return;
	orderParties.getOrderPartyOrOrderAttachment().forEach(ob->{
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
	if ("equipmentList".equalsIgnoreCase(attribute.getName())){
	    List<Object> clearEquip = clearNullEquipment(attribute.getContent());
	    attribute.getContent().clear();
	    attribute.getContent().addAll(clearEquip);
	}
    }
    
    private List<Object> clearNullEquipment(List<Object> obj){
	obj.removeIf(Objects::isNull);
	return obj.stream().filter(o -> {
	    if (o instanceof String){		
		String equipment = ((String) o).trim();		
		if (equipment.contains("[equipment: null]")){		    
		    return false;
		}
	    }
	    return true;
	})
	.collect(Collectors.toList());
    }
}