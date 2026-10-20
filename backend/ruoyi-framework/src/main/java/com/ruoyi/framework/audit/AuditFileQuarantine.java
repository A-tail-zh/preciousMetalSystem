package com.ruoyi.framework.audit;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
/** Preserve damaged source bytes durably before advancing a log cursor. */
final class AuditFileQuarantine {
 private AuditFileQuarantine(){}
 static void preserve(Path source,long offset,byte[] bytes)throws Exception{
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  Path root=source.toAbsolutePath().getParent().resolve("audit-quarantine");Files.createDirectories(root);
  Path target=root.resolve(source.getFileName()+"-"+offset+"-"+hash+".bad");
  if(Files.exists(target)){verify(target,bytes);return;}
  Path temporary=Files.createTempFile(root,"quarantine-",".tmp");
  try{
   try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){
    ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())channel.write(data);channel.force(true);
   }
   try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}
   catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target);}
   catch(FileAlreadyExistsException concurrent){verify(target,bytes);}
  }finally{Files.deleteIfExists(temporary);}
 }
 private static void verify(Path file,byte[] expected)throws java.io.IOException{
  if(!Arrays.equals(Files.readAllBytes(file),expected))throw new java.io.IOException("隔离文件校验失败");
 }
}
