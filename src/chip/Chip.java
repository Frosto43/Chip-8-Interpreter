package chip;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Chip {

	private char[] memory;
	private char[] V;
	private char I;
	private char pc;
	
	private char stack[];
	private int stackPointer;
	
	private int delay_timer;
	private int sound_timer;
	
	private byte[] keys;
	
	private byte[] display;
	
	private boolean needRedraw;
	
	public void init() {
		memory = new char[4096];
		V = new char[16];
		I = 0x0;
		pc = 0x200;
		
		stack = new char[16];
		stackPointer = 0;
		
		delay_timer = 0;
		sound_timer = 0;
		
		keys = new byte[16];
		
		display = new byte[64 * 32];
		
		needRedraw = false;
		loadFontset();
	}
	
	public void run() {
		//fetch opcode
		char opcode = (char)((memory[pc] << 8) | memory[pc + 1]);
		System.out.print(Integer.toHexString(opcode).toUpperCase() + ": ");
		//decode opcode
		switch(opcode & 0xF000) {
		
		case 0x0000: //Multi-case
			switch(opcode & 0x00FF) {
			case 0x00E0: //00E0: Clear Screen
				System.err.println("Unsupported opcode!");
				System.exit(0);
				break;
				
			case 0x00EE: //00EE: Return from Subroutine
				stackPointer--;
				pc = (char)(stack[stackPointer] + 2);
				System.out.println("Returning to " + Integer.toHexString(pc).toUpperCase());
				break;
				
			default: //0NNN: Calls RCA 1802 Program at NNN
				System.err.println("Unsupported opcode!");
				System.exit(0);
				break;					
			}
			break;			
		
		case 0x1000: //1NNN: Jumps to adress NNN
			int nnn = opcode & 0x0FFF;
			pc = (char)nnn;
			System.out.println("Jumping to " + Integer.toHexString(pc).toUpperCase());
			break;
			
		case 0x2000: //2NNN: Calls subroutine at NNN
			stack[stackPointer] = pc;
			stackPointer++;
			pc = (char)(opcode & 0x0FFF);
			System.out.println("Calling " + Integer.toHexString(pc).toUpperCase() + " from " + Integer.toHexString(stack[stackPointer - 1]).toUpperCase());
			break;
			
		case 0x3000: { //3XNN: Skips the next instruction if VX equals NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			if(V[x] == nn) {
				pc += 4;
				System.out.println("Skipping next instruction(V[" + x + "] == " + nn + ")");
			} else {
				pc += 2;
				System.out.println("Not skipping next instruction(V[" + x + "] != " + nn + ")");
			}
			break;
		}
			
		case 0x6000: { // 6XNN: Set VX to NN
			int index = (opcode & 0x0F00) >> 8;
			V[index] = (char)(opcode & 0x00FF);
			pc += 2;
			System.out.println("Setting V[" + index + "] to " + (int)V[index]);
			break;
		}
		
		case 0x7000: { //7XNN: Adds NN to VX
			int index = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			V[index] = (char)((V[index] + nn) & 0xFF);
			pc += 2;
			System.out.println("Adding " + nn + " to V[" + index + "] = " + (int)V[index]);
			break;
		}
		
		case 0x8000: //Contains more data in last nibble
			
			switch(opcode & 0x000F) {
			
			case 0x0000: //8XY0: Sets VX to the Value of VY
			
			default:
				System.err.println("Unsupported opcode!");
				System.exit(0);
				break;
			}
			break;
		
		case 0xA000: //ANNN: Set I to NNN
			I = (char) (opcode & 0x0FFF);
			pc += 2;
			System.out.println("Set I to " + Integer.toHexString(I).toUpperCase());
			break;
			
		case 0xC000: { //CXNN: Set VX to a random number and NN
			int x = (opcode & 0x0F00) >> 8;
			int nn = (opcode & 0x00FF);
			int randomNumber = new Random().nextInt(256) & nn;
			System.out.println("Setting V[" + x + "] to a random number = " + randomNumber);
			V[x] = (char)randomNumber;
			pc += 2;
			
		}
			
		case 0xD000: { //DXYN: Draw sprite (x,y) size (8, n) at I
			int x = V[(opcode & 0x0F00) >> 8];
			int y = V[(opcode & 0x00F0) >> 4];
			int height = opcode & 0x000F;
			
			V[0xF] = 0;
			
			for(int _y = 0; _y < height; _y++){
				int line = memory[I + _y];
				for(int _x = 0; _x < 8; _x++){
					int pixel = line & (0x80 >> _x);
					if(pixel != 0) {
						int totalX = x + _x;
						int totalY = y + _y;
						int index = totalY * 64 + totalX;
						
						if(display[index] == 1)
							V[0xF] = 1;
						
						display[index] ^= 1;
					}
				}
			}
			pc += 2;
			needRedraw = true;
			System.out.println("Drawing at V[" + ((opcode & 0x0F00) >> 8) + "] = " + x + ", V[" + ((opcode & 0x00F0) >> 4) + "] = " + y);
			break;
		}
		
		case 0xE000: {
			switch(opcode & 0x0FF) {
			
			case 0x009E: { //EX9E: Skip the next instruction if the key VX is pressed
				int key = (opcode & 0x0F00) >> 8;
				if(keys[key] == 1) {
					pc += 4;
				} else {
					pc += 2; 
				}
				break;
			}
			
			case 0x00A1: { //EXA1: Skip the next instruction if the key VX is NOT pressed
				int key = (opcode & 0x0F00) >> 8;
				if(keys[key] == 0) {
					pc += 4;
				} else {
					pc += 2; 
				}
				break;
			}
			
			default:
				System.err.println("Unsupported opcode!");
			
			}
			break;
		}
		
		case 0xF000: //Multi-case
			
			switch(opcode & 0x00FF) {
			
			case 0x0007: { //FX07: Set VX to the value of delay_timer
				int x = (opcode & 0x0F00) >> 8;
				V[x] = (char)delay_timer;
				pc += 2;
				System.out.println("Setting V[" + x + "] to delay_timer");
			}
			
			case 0x0015: { //FX15: Set delay timer to V[x]
				int x = (opcode & 0x0F00) >> 8;
				delay_timer = V[x];
				pc += 2;
				System.out.println("Setting delay_timer to V[" + x + "] = " + (int)V[x]);
			}
			
			case 0x0029: { //FX29: Sets I to the location of the sprite for the character VX (fontset)
				int x = (opcode & 0x0F00) >> 8;
				int character = V[x];
				I = (char)(0x050 + (character * 5));
				System.out.println("Setting I to Character V[" + x + "] = " + (int)V[x] + " Offset to " + Integer.toHexString(I).toUpperCase());
				pc += 2;
				break;
			}
			
			case 0x0033: { //FX33: Store a binary-coded decimal value VX in I, I + 1, I + 2
				int x = V[(opcode & 0x0F00) >> 8];
				
				int hundreds = (x - (x % 100)) / 100;
				x -= hundreds * 100;
				int tens = (x - (x % 10)) / 10;
				x -= tens * 10;
				memory[I] = (char)hundreds;
				memory[I + 1] = (char)tens;
				memory[I + 2] = (char)x;
				System.out.println("Storing Binary-Coded Decimal V[" + x + "] = " + (int)(V[(opcode & 0x0F00) >> 8]) + " as {" + hundreds + ", " + tens + ", "+ x + "}");
				pc += 2;
				break;
			}
			
			case 0x0065: { //FX65: Sets V0 to VX with values from I
				int x = (opcode & 0x0F00) >> 8;
				for(int i = 0; i < x; i++) {
					V[i] = memory[I + i];
				}
				System.out.println("Setting V[0] to V[" + x + "] to the values of memory[0x" + Integer.toHexString(I & 0xFFFF).toUpperCase() + "]");
				pc += 2;
				break;
			}
			
			default:
				System.err.println("Unsupported Opcode!");
				System.exit(0);
			}
			break;
			
			default:
				System.err.println("Unsupported Opcode!");
				System.exit(0);
		}
	}
			//execute opcode
	
	
	public byte[] getDisplay() {
		return display;
	}

	public boolean needRedraw() {
		return needRedraw;
	}

	public void removeDrawFlag() {
		needRedraw = false;
	}

	public void loadProgram() {
		DataInputStream input = null;
		try {
			/* Old loading only for Pong2
			 * 
			 * input = new DataInputStream(new FileInputStream(new File(file)));
			
			int offset = 0;
			while(input.available() > 0) {
				memory[0x200 + offset] = (char)(input.readByte() & 0xFF);
				offset++;
			}*/
			
			JFileChooser chooser = new JFileChooser();
			
			FileFilter filter = new FileNameExtensionFilter("C8 Game (*.c8)", "c8");
			
			chooser.setFileFilter(filter);
			chooser.showOpenDialog(chooser);
			
			input = new DataInputStream(new FileInputStream(chooser.getSelectedFile()));
				
			int offset = 0;
			
			while(input.available() > 0) {
				memory[0x200 + offset] = (char) (input.readByte() &0xFF);
				offset++;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(0);
		} finally {
			if(input != null) {
				try { input.close(); } catch (IOException ex) {}
			}
		}
		
	}
	
	public void loadFontset() {
		for(int i = 0; i < ChipData.fontset.length; i++) {
			memory[0x50 + i] = (char)(ChipData.fontset[i] & 0xFF);
		}
	}
	
	public void setKeyBuffer(int[] keyBuffer) {
		for(int i = 0; i < keys.length; i++) {
			keys[i] = (byte)keyBuffer[i];
		}
	}
	
}
