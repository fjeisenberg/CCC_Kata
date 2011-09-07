import static org.junit.Assert.*;

import org.junit.Test;
import static org.mockito.Mockito.*; 
import static org.mockito.Matchers.*; 
import java.net.*;


class Launcher{
	
	public static void main(String[] args){
		def networkReceiver = new NetworkReceiver(new DatagramSocket(9003))
		def sender = new NetworkSender(new DatagramSocket());
		networkReceiver.messageListener = new MessageWorker(sender, new NetworkMessageActionRepository(networkReceiver));
		networkReceiver.listen()
	}
}

interface Sender {
	def sendAnswer(def message);
}
class NetworkSender implements Sender {
	def socket;
	NetworkSender(def socket){
		this.socket = socket
	}
	
	def sendAnswer(def message){
		def buffer = (message.uuid + ":" + message.result).getBytes()
		socket.send(new DatagramPacket(buffer, buffer.length, InetAddress.getByName("localhost"), 9001))
	}
}

interface ActionRepository{
	def findActionFor(def operator)
}
class NetworkMessageActionRepository implements ActionRepository{
	public NetworkMessageActionRepository(def receiver){
		this.receiver = receiver
	}
	def receiver
	def actions = [
		"__SHUTDOWN__":{message -> receiver.close()},
		"ADD":{message -> message.result = message.numbers.sum()},
		"SUBTRACT":{message -> message.result = message.numbers[1..(message.numbers.size()-1)].inject(message.numbers[0]){current, value -> current - value}},
		"MULTIPLY":{message -> message.result = message.numbers.inject(1){currentValue, factor -> currentValue * factor} }
		]
	
	def findActionFor(def operator){
		actions[operator]
	}
	
}

class NetworkReceiver{
	def messageListener;
	def socket;
	NetworkReceiver(def socket){
		this.socket = socket
	}
	def listen(){
		while(!socket.isClosed()){
			def buffer = new byte[1024];
			def packet = new DatagramPacket(buffer, buffer.length);
			socket.receive(packet);
			messageListener.receiveMessage(new MessageParser(new String(packet.getData())).parse());
		}
	}
	def close(){
		socket.close()
	}
}
interface MessageListener{
	void receiveMessage(def message);
}
class MessageWorker implements MessageListener{
	def resultSender
	def actionRepository
	MessageWorker(def resultSender, def actionRepository){
		this.resultSender = resultSender
		this.actionRepository = actionRepository
	}
	void receiveMessage(def message){
		actionRepository.findActionFor(message.operator).call(message)
		resultSender.sendAnswer(message)
	}
}


class MessageParser{
	
	def rawMessage;
	def operator;
	def uuid;
	def numbers = []

	
	MessageParser(def rawMessage){
		this.rawMessage = rawMessage;
	}
	Message parse(){
		extractContents()
		return new Message([operator:this.operator, uuid:this.uuid, numbers: this.numbers]);
	}
	
	private extractContents() {
		def contents = rawMessage.split(":")
		contents = contents.collect{it.trim()}
		operator = contents[0]
		if(contents.size() > 1){
			this.uuid = contents[1]
			numbers = contents[2..(contents.size()-1)].collect { it.toLong()}
		}
	}
}
class Message{
	def operator
	def uuid
	def numbers = []
	def result
	
	public boolean equals(Object o){
			if(uuid != null) return uuid.equals(o.uuid)
			else return operator.equals(o.operator)
	}
}

class MessageParserTest{
	
	@Test
	public void parsesOperator(){
		assert parseMessage("operator1:uuid:1:2").operator == "operator1"  
		assert parseMessage("operator2:uuid:1:2").operator == "operator2"
	}
	
	@Test
	public void parsesUuid(){
		assert parseMessage("operator:uuid1:1:2").uuid == "uuid1"  
		assert parseMessage("operator:uuid2:1:2").uuid == "uuid2"
	}
	
	@Test
	public void parsesParams(){
		def message = parseMessage("operator:uuid1:3:4:5")
		assert message.numbers[0] == 3
		assert message.numbers[1] == 4
		assert message.numbers[2] == 5
	}
	@Test
	public void parsesCommands(){
		assert parseMessage("__SHUTDOWN__").operator == "__SHUTDOWN__"
	}
	
	def parseMessage(def message){
		new MessageParser(message).parse()
	}
}
class MessageReceiverTest{
	@Test
	public void testActionIsTaken() throws Exception {
		def messageToSend = new Message(operator:"doSomething")
		
		def sender = mock(Sender.class);
		def repository = mock(ActionRepository.class);
		
		when(repository.findActionFor("doSomething")).thenReturn({});
		
		def receiver = new MessageWorker(sender, repository);
		
		receiver.receiveMessage(messageToSend)
		
		verify(repository).findActionFor("doSomething")
		verify(sender).sendAnswer(messageToSend)
	}
}

class ReceiverTest {
	
	@Test
	public void testReceivesMessages(){
		def MESSAGE = "This is a non-well-formed message";
		def messageReceiver = mock(MessageListener.class);
		def socket = aSocket().receivingAMessage(MESSAGE).build()
		
		def receiver = new NetworkReceiver(socket);
		receiver.messageListener = messageReceiver;
		
		receiver.listen();
		
		verify(messageReceiver).receiveMessage(new Message(operator:MESSAGE));
		assert socket.hasReceivedMessage()
	}
	
	private def aSocket(){
		new DatagramSocketBuilder()
	}
}

class DatagramSocketBuilder{
	def messageToReceive;
	def messageReceived = false
	def build (){
		["receive":{datagramPacket-> datagramPacket.setData(messageToReceive.getBytes()); messageReceived = true},
		"hasReceivedMessage":{messageReceived}].asType(DatagramSocket.class);
	}
	
	def receivingAMessage(def message){
		messageToReceive = message;
		this
	}
}
public class NetworkSenderTest{
	@Test
	public void testSending(){
		def socket = mock(DatagramSocket.class)
		def sender = new NetworkSender(socket)
		
		sender.sendAnswer(new Message())
		
		verify(socket).send(any(DatagramPacket.class))
	}
}
