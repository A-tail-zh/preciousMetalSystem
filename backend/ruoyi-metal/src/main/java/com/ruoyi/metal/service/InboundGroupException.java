package com.ruoyi.metal.service;
import com.ruoyi.common.exception.ServiceException;
public class InboundGroupException extends RuntimeException {
 private final int index;
 public InboundGroupException(int index,String message){super(message);this.index=index;}
 public int getIndex(){return index;}
}
