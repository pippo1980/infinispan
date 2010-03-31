package org.infinispan.client.hotrod.impl;

import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.infinispan.client.hotrod.exceptions.InvalidResponseException;
import org.infinispan.client.hotrod.exceptions.TimeoutException;
import org.infinispan.client.hotrod.impl.transport.TransportException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * // TODO: Document this
 *
 * @author mmarkus
 * @since 4.1
 */
public class HotrodOperationsImpl implements HotrodOperations, HotrodConstants {

   private static Log log = LogFactory.getLog(HotrodOperationsImpl.class);

   private final byte[] cacheNameBytes;
   private static final AtomicLong MSG_ID = new AtomicLong();
   private TransportFactory transportFactory;
   private byte clientIntelligence;

   public HotrodOperationsImpl(String cacheName, TransportFactory transportFactory) {
      cacheNameBytes = cacheName.getBytes(); //todo add charset here
      this.transportFactory = transportFactory;
   }

   public byte[] get(byte[] key, Flag[] flags) {
      Transport transport = getTransport();
      try {
         short status = sendKeyOperation(key, transport, GET_REQUEST, flags, GET_RESPONSE);
         if (status == KEY_DOES_NOT_EXIST_STATUS) {
            return null;
         }
         if (status == NO_ERROR_STATUS) {
            return transport.readByteArray();
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }

   public byte[] remove(byte[] key, Flag[] flags) {
      Transport transport = getTransport();
      try {
         short status = sendKeyOperation(key, transport, REMOVE_REQUEST, flags, REMOVE_RESPONSE);
         if (status == KEY_DOES_NOT_EXIST_STATUS) {
            return null;
         } else if (status == NO_ERROR_STATUS) {
            return returnPossiblePrevValue(transport, flags);
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }

   public boolean containsKey(byte[] key, Flag... flags) {
      Transport transport = getTransport();
      try {
         short status = sendKeyOperation(key, transport, CONTAINS_KEY_REQUEST, flags, CONTAINS_KEY_RESPONSE);
         if (status == KEY_DOES_NOT_EXIST_STATUS) {
            return false;
         } else if (status == NO_ERROR_STATUS) {
            return true;
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }

   public BinaryVersionedValue getWithVersion(byte[] key, Flag... flags) {
      Transport transport = getTransport();
      try {
         short status = sendKeyOperation(key, transport, GET_WITH_CAS_REQUEST, flags, GET_WITH_CAS_RESPONSE);
         if (status == KEY_DOES_NOT_EXIST_STATUS) {
            return null;
         }
         if (status == NO_ERROR_STATUS) {
            long version = transport.readVLong();
            byte[] value = transport.readByteArray();
            return new BinaryVersionedValue(version, value);
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }


   public byte[] put(byte[] key, byte[] value, int lifespan, int maxIdle, Flag... flags) {
      Transport transport = getTransport();
      try {
         short status = sendPutOperation(key, value, transport, PUT_REQUEST, PUT_RESPONSE, lifespan, maxIdle, flags);
         if (status != NO_ERROR_STATUS) {
            throw new InvalidResponseException("Unexpected response status: " + Integer.toHexString(status));
         }
         return returnPossiblePrevValue(transport, flags);
      } finally {
         releaseTransport(transport);
      }
   }

   public byte[] putIfAbsent(byte[] key, byte[] value, int lifespan, int maxIdle, Flag... flags) {
      Transport transport = getTransport();
      try {
         short status = sendPutOperation(key, value, transport, PUT_IF_ABSENT_REQUEST, PUT_IF_ABSENT_RESPONSE, lifespan, maxIdle, flags);
         if (status == NO_ERROR_STATUS) {
            return returnPossiblePrevValue(transport, flags);
         } else if (status == NOT_PUT_REMOVED_REPLACED_STATUS) {
            return null;
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }

   public byte[] replace(byte[] key, byte[] value, int lifespan, int maxIdle, Flag... flags) {
      Transport transport = getTransport();
      try {
         short status = sendPutOperation(key, value, transport, REPLACE_REQUEST, REPLACE_RESPONSE, lifespan, maxIdle, flags);
         if (status == NO_ERROR_STATUS) {
            return returnPossiblePrevValue(transport, flags);
         } else if (status == NOT_PUT_REMOVED_REPLACED_STATUS) {
            return null;
         }
      } finally {
         releaseTransport(transport);
      }
      throw new IllegalStateException("We should not reach here!");
   }

   /**
    * request : [header][key length][key][lifespan][max idle][entry_version][value length][value] response: If
    * ForceReturnPreviousValue has been passed, this responses will contain previous [value length][value] for that key.
    * If the key does not exist or previous was null, value length would be 0. Otherwise, if no ForceReturnPreviousValue
    * was sent, the response would be empty.
    */
   public VersionedOperationResponse replaceIfUnmodified(byte[] key, byte[] value, int lifespan, int maxIdle, long version, Flag... flags) {
      Transport transport = getTransport();
      try {
         // 1) write header
         long messageId = writeHeader(transport, REPLACE_IF_UNMODIFIED_REQUEST, flags);

         //2) write message body
         transport.writeByteArray(key);
         transport.writeVInt(lifespan);
         transport.writeVInt(maxIdle);
         transport.writeVLong(version);
         transport.writeByteArray(value);
         return returnVersionedOperationResponse(transport, messageId, flags);
      } finally {
         releaseTransport(transport);
      }
   }

   /**
    * Request: [header][key length][key][entry_version]
    */
   public VersionedOperationResponse removeIfUnmodified(byte[] key, long version, Flag... flags) {
      Transport transport = getTransport();
      try {
         // 1) write header
         long messageId = writeHeader(transport, REMOVE_IF_UNMODIFIED_REQUEST, flags);

         //2) write message body
         transport.writeByteArray(key);
         transport.writeVLong(version);

         //process response and return
         return returnVersionedOperationResponse(transport, messageId, flags);

      } finally {
         releaseTransport(transport);
      }
   }

   public void clear(Flag... flags) {
      Transport transport = getTransport();
      try {
         // 1) write header
         long messageId = writeHeader(transport, CLEAR_REQUEST, flags);
         readHeaderAndValidate(transport, messageId, CLEAR_RESPONSE);
      } finally {
         releaseTransport(transport);
      }
   }

   public Map<String, Number> stats() {
      Transport transport = getTransport();
      try {
         // 1) write header
         long messageId = writeHeader(transport, STATS_REQUEST);
         readHeaderAndValidate(transport, messageId, CLEAR_RESPONSE);
         int nrOfStats = transport.readVInt();
         Map<String, Number> result = new HashMap<String, Number>();
         for (int i = 0; i < nrOfStats; i++) {
            String statName = transport.readString();
            Long statValue = transport.readVLong();
            result.put(statName, statValue);
         }
         return result;
      } finally {
         releaseTransport(transport);
      }
   }

   public Transport getTransport() {
      return transportFactory.getTransport();
   }

   @Override
   public boolean ping() {
      Transport transport = null;
      try {
         transport = getTransport();
         // 1) write header
         long messageId = writeHeader(transport, PING_REQUEST);
         short respStatus = readHeaderAndValidate(transport, messageId, HotrodConstants.PING_RESPONSE);
         if (respStatus == NO_ERROR_STATUS) {
            return true;
         }
         throw new IllegalStateException("Unknown response status: " + Integer.toHexString(respStatus));
      } catch (TransportException te) {
         log.trace("Exception while ping", te);
         return false;
      }
      finally {
         releaseTransport(transport);
      }
   }

   //[header][key length][key][lifespan][max idle][value length][value]

   private short sendPutOperation(byte[] key, byte[] value, Transport transport, short opCode, byte opRespCode, int lifespan, int maxIdle, Flag[] flags) {
      // 1) write header
      long messageId = writeHeader(transport, opCode, flags);

      // 2) write key and value
      transport.writeByteArray(key);
      transport.writeVInt(lifespan);
      transport.writeVInt(maxIdle);
      transport.writeByteArray(value);
      transport.flush();

      // 3) now read header

      //return status (not error status for sure)
      return readHeaderAndValidate(transport, messageId, opRespCode);
   }

   /*
    * Magic	| MessageId	| Version | Opcode | CacheNameLength | CacheName | Flags | Client Intelligence | Topology Id
    */

   private long writeHeader(Transport transport, short operationCode, Flag... flags) {
      transport.writeByte(REQUEST_MAGIC);
      long messageId = MSG_ID.incrementAndGet();
      transport.writeVLong(messageId);
      transport.writeByte(HOTROD_VERSION);
      transport.writeByte(operationCode);
      transport.writeByteArray(cacheNameBytes);
      int flagInt = 0;
      if (flags != null) {
         for (Flag flag : flags) {
            flagInt = flag.getFlagInt() | flagInt;
         }
      }
      transport.writeVInt(flagInt);
      transport.writeByte(clientIntelligence);
      transport.writeVInt(0);//this will be changed once smarter clients are supported
      return messageId;
   }

   /**
    * Magic	| Message Id | Op code | Status | Topology Change Marker
    */
   private short readHeaderAndValidate(Transport transport, long messageId, short opRespCode) {
      short magic = transport.readByte();
      if (magic != RESPONSE_MAGIC) {
         throw new InvalidResponseException("Invalid magic number. Expected " + Integer.toHexString(RESPONSE_MAGIC) + " and received " + Integer.toHexString(magic));
      }
      long receivedMessageId = transport.readVLong();
      if (receivedMessageId != messageId) {
         throw new InvalidResponseException("Invalid message id. Expected " + Long.toHexString(messageId) + " and received " + Long.toHexString(receivedMessageId));
      }
      short receivedOpCode = transport.readByte();
      if (receivedOpCode != opRespCode) {
         if (receivedOpCode == ERROR_RESPONSE) {
            checkForErrorsInResponseStatus(transport.readByte(), messageId, transport);
            throw new IllegalStateException("Error expected! (i.e. exception in the prev statement)");
         }
         throw new InvalidResponseException("Invalid response operation. Expected " + Integer.toHexString(opRespCode) + " and received " + Integer.toHexString(receivedOpCode));
      }
      short status = transport.readByte();
      transport.readByte(); //todo - this will be changed once we support smarter than basic clients
      checkForErrorsInResponseStatus(status, messageId, transport);
      return status;
   }

   private void checkForErrorsInResponseStatus(short status, long messageId, Transport transport) {
      switch ((int) status) {
         case INVALID_MAGIC_OR_MESSAGE_ID_STATUS:
         case REQUEST_PARSING_ERROR_STATUS:
         case UNKNOWN_COMMAND_STATUS:
         case SERVER_ERROR_STATUS:
         case UNKNOWN_VERSION_STATUS: {
            throw new HotRodClientException(transport.readString(), messageId, status);
         }
         case COMMAND_TIMEOUT_STATUS: {
            throw new TimeoutException();
         }
         case NO_ERROR_STATUS:
         case KEY_DOES_NOT_EXIST_STATUS: {
            //don't do anything, these are correct responses
            break;
         }
         default: {
            throw new IllegalStateException("Unknown status: " + Integer.toHexString(status));
         }
      }
   }

   private boolean hasForceReturn(Flag[] flags) {
      if (flags == null) return false;
      for (Flag flag : flags) {
         if (flag == Flag.FORCE_RETURN_VALUE) return true;
      }
      return false;
   }

   private short sendKeyOperation(byte[] key, Transport transport, byte opCode, Flag[] flags, byte opRespCode) {
      // 1) write [header][key length][key]
      long messageId = writeHeader(transport, opCode, flags);
      transport.writeByteArray(key);
      transport.flush();

      // 2) now read the header
      return readHeaderAndValidate(transport, messageId, opRespCode);
   }

   private byte[] returnPossiblePrevValue(Transport transport, Flag[] flags) {
      return hasForceReturn(flags) ? transport.readByteArray() : null;
   }

   private void releaseTransport(Transport transport) {
      if (transport != null)
         transport.release();
   }

   private VersionedOperationResponse returnVersionedOperationResponse(Transport transport, long messageId, Flag[] flags) {
      //3) ...
      short respStatus = readHeaderAndValidate(transport, messageId, REPLACE_IF_UNMODIFIED_RESPONSE);

      //4 ...
      VersionedOperationResponse.RspCode code;
      if (respStatus == NO_ERROR_STATUS) {
         code = VersionedOperationResponse.RspCode.SUCCESS;
      } else if (respStatus == NOT_PUT_REMOVED_REPLACED_STATUS) {
         code = VersionedOperationResponse.RspCode.MODIFIED_KEY;
      } else if (respStatus == KEY_DOES_NOT_EXIST_STATUS) {
         code = VersionedOperationResponse.RspCode.NO_SUCH_KEY;
      } else {
         throw new IllegalStateException("Unknown response status: " + Integer.toHexString(respStatus));
      }
      byte[] prevValue = returnPossiblePrevValue(transport, flags);
      return new VersionedOperationResponse(prevValue, code);
   }
}