import java.awt.event.*;
import java.util.Arrays;
import java.util.LinkedList;
import com.jogamp.opengl.*;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.glu.*;
import com.jogamp.opengl.awt.GLJPanel;
import javax.swing.*;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.gl2.GLUT;

/**
 * ============> Instructions <============ L, R, U, D, F, B => CW Face rotation
 * l, r, u, d, f, b => CCW Face rotation a => Random face rotation s => 100
 * random rotations ======> While shuffling, rotations are immediate, but still
 * drawn as a single frame, like a computer console effect from a 60's movie. n
 * => Undo the previous face rotation x, NUMPAD_1 => Rotate cube on its x-axis
 * (<SHIFT> or <CAPS_LOCK> to reverse) y, NUMPAD_2 => Rotate cube on its y-axis
 * (<SHIFT> or <CAPS_LOCK> to reverse) z, NUMPAD_3 => Rotate cube on its y-axis
 * (<SHIFT> or <CAPS_LOCK> to reverse) 0, NUMPAD_0 => Return cube to its
 * original orientation
 * 
 * ============> CIRCLE-STRAFE!!! <============ NUMPAD_4 => Circle-strafe left
 * NUMPAD_6 => Circle-strafe right SPACEBAR => Instantly move point of view to
 * the opposite side of the cube ==========> The up vector and all 3 eye coords
 * are negated, approximating the viewer flipping the cube over in their hands.
 * PAGE_UP => Move camera closer to the cube PAGE_DOWN => Move camera further
 * from the cube NUMPAD_5 => Return to original viewing position and orientation
 * 
 * ============> CHEAT CODES!!1 <============ ==> Okay, so there's only one
 * cheat code, but I'm keeping the 'S' ==> UP, UP, DOWN, DOWN, LEFT, RIGHT,
 * LEFT, RIGHT, A, B ==> Konami mode!
 * 
 * ============> IMPORTANT! <============ ===> Hold down CONTROL while entering
 * codes => NO other modifier keys ===> Do not release CONTROL until entire code
 * has been entered ===> Cheat codes are triggered when CONTROL is released ===>
 * You should see console output informing you of code progress =======>
 * Auto-win! That's what the code does. It wins Rubik's Cube. =======> Solves
 * the cube in reasonable time by scaling the animation speed =======> You may
 * move and rotate the cube while it is solving =======> It will attempt to
 * ignore incoming face rotations, but if one slips through, I think it could
 * handle it.
 * 
 * 
 * ==> Optionally, the MyRubicCube class instantiates one thimbleful of Wayne
 * Gretzky's tears. After the thimble is broken upon an icy surface, but before
 * the tears fully evaporate, the wielder may utter the phrase "Ninety percent
 * of hockey is mental and the other half is physical, eh." If so invoked, the
 * future ghost of Wayne Gretzky, mounted upon a spectral Zamboni, will
 * manifest. Non-Canadians should practice their accent ahead of time, as the
 * duration of the manifestation is determined by the "Canadian-ness" of the
 * user's accent. Possible durations are 2-minute minor, double-minor, and
 * triple-minor, with the current two-minute "clock" expiring each time a foe is
 * dispatched.
 */
public class MyRubikCube extends JFrame implements GLEventListener, KeyListener {
	// cubie face membership arrays
	static final int[] RIGHT = { 2, 11, 20, 23, 26, 17, 8, 5, 14 };
	static final int[] LEFT = { 21, 24, 15, 6, 3, 0, 9, 18, 12 };
	static final int[] UP = { 18, 19, 20, 11, 2, 1, 0, 9, 10 };
	static final int[] DOWN = { 6, 15, 24, 25, 26, 17, 8, 7, 16 };
	static final int[] FRONT = { 0, 1, 2, 5, 8, 7, 6, 3, 4 };
	static final int[] BACK = { 24, 21, 18, 19, 20, 23, 26, 25, 22 };

	GLU glu;
	GLUT glut;
	static GLCapabilities caps = new GLCapabilities(GLProfile.getGL2GL3());;
	static FPSAnimator animator;
	static Cubie[] cubies = new Cubie[27];
	static LinkedList<Integer> history = new LinkedList<Integer>();

	static double eye[] = { 3.0, 2.0, 4.0 };
	static double look[] = { 0.0, 0.0, 0.0 };
	static double up[] = { 0.0, 1.0, 0.0 };
	static double range = 8.08;

	static int[] theta = { 0, 0, 0 };// rubric rotations
	static int move = 0, nextMove = 0, scramble = 0, maxUndos = 1, cheat = -1,
			frame = 0, numFrames = 30;
	static int speed = 90 / numFrames;// animation speed factor

	static boolean keyShift = false, undoing = false, undoable = false;

	public MyRubikCube() {
		super("MyRubikCube");
	}

	public static void main(String[] args) {
		caps.setDoubleBuffered(true);
		caps.setHardwareAccelerated(true);
		GLJPanel canvas = new GLJPanel();
		MyRubikCube rubric = new MyRubikCube();
		canvas.addGLEventListener(rubric);
		canvas.addKeyListener(rubric);
		JFrame frame = new JFrame("MyRubikCube");
		frame.setSize(800, 800);
		frame.setExtendedState(MAXIMIZED_BOTH);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().add(canvas);
		frame.setVisible(true);
		canvas.requestFocusInWindow();
		animator = new FPSAnimator(canvas, 60);
		rubric.run();
	}

	/**
	 * Normalize eye position to current distance in order to prevent drift when
	 * moving.
	 */
	public void normEye() {
		range = Math.max(range, 6.9);
		range = Math.min(range, 13.0);
		double d = range
				/ Math.sqrt(eye[0] * eye[0] + eye[1] * eye[1] + eye[2] * eye[2]);
		eye[0] *= d;
		eye[1] *= d;
		eye[2] *= d;
	}

	/**
	 * Progressively rotate a face from 0 to theta degrees about the vector x,
	 * y, z. Rotations are broken into NUM_FRAMES frames, with each call to
	 * rotateFace representing one frame.
	 * 
	 * @param gl
	 * @param face
	 * @param theta
	 * @param x
	 * @param y
	 * @param z
	 */
	void rotateFace(GL2 gl, int[] face, double theta, int x, int y, int z) {
		frame++;
		for (int i = 0; i < 9; i++) {
			gl.glPushMatrix();
			gl.glRotated(theta * frame / numFrames, x, y, z);
			gl.glMultMatrixd(cubies[face[i]].matrix, 0);
			gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX,
					cubies[face[i]].drawMatrix, 0);
			// commit transformation only on final frame
			if (frame == numFrames) {
				cubies[face[i]].commit();
			}
			gl.glPopMatrix();
		}
		if (frame == numFrames) {// final frame
			Cubie[] temp = new Cubie[8];
			for (int i = 0; i < 8; i++) {
				temp[i] = cubies[face[i]];
			}
			for (int i = 0; i < 8; i++) {// update cubie references
				cubies[face[i]] = temp[(i + 8 + 2 * ((int) theta / 90)) % 8];
			}
			if (!undoing) {// don't add undos to history
				history.push(move);
				undoable = true;
			}
			undoing = false;
			move = frame = 0;
		}
	}

	/**
	 * Process the current move.
	 * 
	 * @param gl
	 */
	void move(GL2 gl) {
		if (scramble != 0) {// if scrambling
			frame = numFrames - 1;// don't animate
			move = 12;// random move
			scramble = (scramble < 100 ? ++scramble : 0);
		} else if (move == 0) {// else get next move
			move = nextMove;
			nextMove = 0;
		}

		switch (move) {// special moves
		case 10: // undo
			if (history.isEmpty() | !undoable) {
				move = 0;
			} else {
				move = history.pop() * -1;
				undoable = false;
				undoing = true;
			}
			break;
		case 11: // begin scramble and do first random move
			scramble = 1;
		case 12:// random
			move = (int) (Math.random() * 6) + 1;
			move *= Math.random() < 0.5 ? 1 : -1;
			break;
		}

		switch (move) {// normal moves
		case 1:
			rotateFace(gl, RIGHT, -90, 1, 0, 0);
			break;
		case 2:
			rotateFace(gl, LEFT, 90, 1, 0, 0);
			break;
		case 3:
			rotateFace(gl, UP, -90, 0, 1, 0);
			break;
		case 4:
			rotateFace(gl, DOWN, 90, 0, 1, 0);
			break;
		case 5:
			rotateFace(gl, FRONT, -90, 0, 0, 1);
			break;
		case 6:
			rotateFace(gl, BACK, 90, 0, 0, 1);
			break;
		case -1:
			rotateFace(gl, RIGHT, 90, 1, 0, 0);
			break;
		case -2:
			rotateFace(gl, LEFT, -90, 1, 0, 0);
			break;
		case -3:
			rotateFace(gl, UP, 90, 0, 1, 0);
			break;
		case -4:
			rotateFace(gl, DOWN, -90, 0, 1, 0);
			break;
		case -5:
			rotateFace(gl, FRONT, 90, 0, 0, 1);
			break;
		case -6:
			rotateFace(gl, BACK, -90, 0, 0, 1);
			break;
		}
	}

	/**
	 * Checks upcoming rotations for simple reducible patterns
	 */
	public void lookahead() {
		Integer[] peek = new Integer[4];
		history.subList(0, 4).toArray(peek);
		if (peek[0] == -peek[1]) {
			// x+ x- == 0
			history.pop();
			history.pop();
		} else if (peek[0] == -peek[3] && peek[1] == -peek[2]) {
			// x+ y+ y- x- == 0
			history.pop();
			history.pop();
			history.pop();
			history.pop();
		} else if (peek[0] == peek[1] && peek[1] == peek[2]
				&& peek[2] == peek[3]) {
			// x+ x+ x+ x+ == 0
			history.pop();
			history.pop();
			history.pop();
			history.pop();
		} else if (peek[0] == peek[1] && peek[1] == peek[2]) {
			// x+ x+ x+ == x-
			history.pop();
			history.pop();
			history.push(history.pop() * -1);
		}
	}

	/**
	 * Enter cheat mode, which solves the cube. Interestingly enough, I do this
	 * by cheating...I simple undo the move history. I did add a look-ahead
	 * which detects and eliminates some simple, but common, cycles.
	 * 
	 * @param gl
	 */
	public void konami(GL2 gl) {
		int odds = (int) (Math.random() * 100.0);
		undoable = true;
		if (history.size() == 0) {
			numFrames = 30;
			cheat = -1;
			undoable = false;
		} else if (history.size() < 4) {
			numFrames = 30;
			nextMove = (odds > 1 ? 10 : 12);
		} else if (history.size() < 10) {
			lookahead();
			numFrames = 20;
			nextMove = (odds > 10 ? 10 : 12);
		} else if (cheat <= -1337) {
			lookahead();
			numFrames = 10;
			nextMove = (odds > 0 ? 10 : 12);
		} else if (cheat > -1324) {
			lookahead();
			numFrames = 20;
			nextMove = (odds > 30 ? 10 : 12);
		} else if (cheat > -1316) {
			lookahead();
			numFrames = 30;
			nextMove = (odds > 90 ? 10 : 12);
		}
		cheat--;
	}

	public void run() {
		animator.start();
	}

	public void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		glu = new GLU();
		glut = new GLUT();
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glClearColor(0.5f, 0.5f, 0.5f, 0.0f);

		normEye();
		for (int id = 0; id < 27; id++) {
			cubies[id] = new Cubie(id, gl);
		}
	}

	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glShadeModel(GL2.GL_FLAT);
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

		if (cheat <= -1313) {
			gl.glClearColor(0.6f, 0.3f, 0.3f, 0.0f);
			konami(gl);
		} else {
			gl.glClearColor(0.5f, 0.5f, 0.5f, 0.0f);
		}
		// transform cubies
		gl.glLoadIdentity();
		move(gl);
		// look at rubric
		glu.gluLookAt(eye[0], eye[1], eye[2], 0.0, 0.0, 0.0, up[0], up[1],
				up[2]);
		// rotate rubric
		gl.glRotated(theta[0], 1, 0, 0);
		gl.glRotated(theta[1], 0, 1, 0);
		gl.glRotated(theta[2], 0, 0, 1);
		// draw cubies
		for (int id = 0; id < 27; id++) {
			cubies[id].draw(gl);
		}
		gl.glFlush();
	}

	public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
		GL2 gl = drawable.getGL().getGL2();
		gl.glViewport(0, 0, w, h);
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();
		glu.gluPerspective(45.0, (double) w / (double) h, 1.0, 20.0);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
	}

	public void displayChanged(GLAutoDrawable drawable, boolean modeChanged,
			boolean deviceChanged) {
	}

	public void dispose(GLAutoDrawable arg0) {
	}

	public void keyTyped(KeyEvent key) {
	}

	public void keyPressed(KeyEvent key) {
		if (cheat < 0) {
			switch (key.getKeyCode()) {
			case KeyEvent.VK_ESCAPE:
				// The animator must be stopped in a different thread to
				// make sure that it completes before exit is called.
				new Thread() {
					public void run() {
						animator.stop();
					}
				}.start();
				System.exit(0);
				break;
			// key modifiers
			case KeyEvent.VK_SHIFT:
				keyShift = keyShift ? false : true;
				break;
			case KeyEvent.VK_CAPS_LOCK:
				keyShift = keyShift ? false : true;
				break;
			case KeyEvent.VK_CONTROL:
				cheat = 0;
				break;
			// normal moves
			case KeyEvent.VK_R:
				nextMove = keyShift ? -1 : 1;
				break;
			case KeyEvent.VK_L:
				nextMove = keyShift ? -2 : 2;
				break;
			case KeyEvent.VK_U:
				nextMove = keyShift ? -3 : 3;
				break;
			case KeyEvent.VK_D:
				nextMove = keyShift ? -4 : 4;
				break;
			case KeyEvent.VK_F:
				nextMove = keyShift ? -5 : 5;
				break;
			case KeyEvent.VK_B:
				nextMove = keyShift ? -6 : 6;
				break;
			// special moves
			case KeyEvent.VK_N:// undo
				nextMove = 10;
				break;
			case KeyEvent.VK_S:// shuffle
				nextMove = 11;
				break;
			case KeyEvent.VK_A:// random
				nextMove = 12;
				break;
			// orient rubric
			case KeyEvent.VK_NUMPAD1:
			case KeyEvent.VK_X:
				theta[0] = (theta[0] + speed * (keyShift ? -1 : 1)) % 360;
				break;
			case KeyEvent.VK_NUMPAD2:
			case KeyEvent.VK_Y:
				theta[1] = (theta[1] + speed * (keyShift ? -1 : 1)) % 360;
				break;
			case KeyEvent.VK_NUMPAD3:
			case KeyEvent.VK_Z:
				theta[2] = (theta[2] + speed * (keyShift ? -1 : 1)) % 360;
				break;
			case KeyEvent.VK_NUMPAD0:// reset orientation
			case KeyEvent.VK_0:
				theta[0] = theta[1] = theta[2] = 0;
				break;
			// move viewer
			case KeyEvent.VK_SPACE:
				eye[0] *= -1;
				eye[1] *= -1;
				eye[2] *= -1;
				up[1] *= -1;
				break;
			case KeyEvent.VK_PAGE_UP:
				range -= 0.1 * speed;
				normEye();
				break;
			case KeyEvent.VK_PAGE_DOWN:
				range += 0.1 * speed;
				normEye();
				break;
			case KeyEvent.VK_NUMPAD4:// left rotate eye about y axis
				eye[0] = Math.cos(Math.PI / -180 * speed) * eye[0]
						+ Math.sin(Math.PI / -180 * speed) * eye[2];
				eye[2] = -Math.sin(Math.PI / -180 * speed) * eye[0]
						+ Math.cos(Math.PI / -180 * speed) * eye[2];
				normEye();
				break;
			case KeyEvent.VK_NUMPAD5:// reset eye
				eye[0] = 3.0;
				eye[1] = 2.0;
				eye[2] = 4.0;
				up[1] = 1.0;
				range = 8.08;
				normEye();
				break;
			case KeyEvent.VK_NUMPAD6:// right rotate eye about y axis
				eye[0] = Math.cos(Math.PI / 180 * speed) * eye[0]
						+ Math.sin(Math.PI / 180 * speed) * eye[2];
				eye[2] = -Math.sin(Math.PI / 180 * speed) * eye[0]
						+ Math.cos(Math.PI / 180 * speed) * eye[2];
				normEye();
				break;
			}
		} else {
			switch (key.getKeyCode()) {
			case KeyEvent.VK_UP:
				cheat = (cheat == 0 || cheat == 1) ? ++cheat : 0;
				break;
			case KeyEvent.VK_DOWN:
				cheat = (cheat == 2 || cheat == 3) ? ++cheat : 0;
				break;
			case KeyEvent.VK_LEFT:
				cheat = (cheat == 4 || cheat == 6) ? ++cheat : 0;
				break;
			case KeyEvent.VK_RIGHT:
				cheat = (cheat == 5 || cheat == 7) ? ++cheat : 0;
				break;
			case KeyEvent.VK_A:
				cheat = (cheat == 8) ? ++cheat : 0;
				break;
			case KeyEvent.VK_B:
				cheat = (cheat == 9) ? ++cheat : 0;
				break;
			default:
				cheat = 0;
			}
			System.out.println((10 - cheat) + " steps away!");
		}
	}

	public void keyReleased(KeyEvent key) {
		switch (key.getKeyCode()) {
		case KeyEvent.VK_SHIFT:
			keyShift = keyShift ? false : true;
			break;
		case KeyEvent.VK_CONTROL:
			if (cheat == 10) {
				cheat = -1313;
				System.out.println("Cheater!");
			} else {
				cheat = -1;
			}
			break;
		}
	}
}

/**
 * Represents a single cubie, it's face colors and it's accumulated face
 * rotation transformations.
 */
class Cubie {
	static final double cubieVerts[][] = { { -0.5, -0.5, 0.5 }, // vertex 0
			{ -0.5, 0.5, 0.5 }, // 1
			{ 0.5, 0.5, 0.5 }, // 2
			{ 0.5, -0.5, 0.5 }, // 3
			{ -0.5, -0.5, -0.5 }, // 4
			{ -0.5, 0.5, -0.5 }, // 5
			{ 0.5, 0.5, -0.5 }, // 6
			{ 0.5, -0.5, -0.5 } }; // 7
	static final double colors[][] = { { 0.13, 0.13, 0.13 }, // black 0
			{ 0.60, 0.00, 0.00 }, // red 1
			{ 0.80, 0.40, 0.00 }, // orange 2
			{ 0.00, 0.00, 0.60 }, // blue 3
			{ 0.00, 0.60, 0.00 }, // green 4
			{ 0.80, 0.80, 0.80 }, // white 5
			{ 0.80, 0.80, 0.00 } }; // yellow 6

	int id;
	int[] faceColors;
	double[] drawMatrix, matrix;

	/**
	 * Cubie constructor which determines cubie's face colors and translates it
	 * into position.
	 * 
	 * @param id
	 *            cubie's identifier
	 * @param gl
	 */
	public Cubie(int id, GL2 gl) {
		this.id = id;
		drawMatrix = new double[16];

		gl.glPushMatrix();
		gl.glLoadIdentity();

		switch (id) {
		// = = = = = FRONT layer = = = = =
		case 0: // LEFT-TOP-FRONT cubie
			faceColors = new int[] { 0, 2, 3, 0, 5, 0 };
			gl.glTranslated(-1.0, 1.0, 1.0);
			break;
		case 1: // MID-TOP-FRONT cubie
			faceColors = new int[] { 0, 0, 3, 0, 5, 0 };
			gl.glTranslated(0.0, 1.0, 1.0);
			break;
		case 2: // RIGHT-TOP-FRONT cubie
			faceColors = new int[] { 1, 0, 3, 0, 5, 0 };
			gl.glTranslated(1.0, 1.0, 1.0);
			break;
		case 3: // LEFT-MID-FRONT cubie
			faceColors = new int[] { 0, 2, 0, 0, 5, 0 };
			gl.glTranslated(-1.0, 0.0, 1.0);
			break;
		case 4: // MID-MID-FRONT cubie
			faceColors = new int[] { 0, 0, 0, 0, 5, 0 };
			gl.glTranslated(0.0, 0.0, 1.0);
			break;
		case 5: // RIGHT-MID-FRONT cubie
			faceColors = new int[] { 1, 0, 0, 0, 5, 0 };
			gl.glTranslated(1.0, 0.0, 1.0);
			break;
		case 6: // LEFT-DOWN-FRONT cubie
			faceColors = new int[] { 0, 2, 0, 4, 5, 0 };
			gl.glTranslated(-1.0, -1.0, 1.0);
			break;
		case 7: // MID-DOWN-FRONT cubie
			faceColors = new int[] { 0, 0, 0, 4, 5, 0 };
			gl.glTranslated(0.0, -1.0, 1.0);
			break;
		case 8: // RIGHT-DOWN-FRONT cubie
			faceColors = new int[] { 1, 0, 0, 4, 5, 0 };
			gl.glTranslated(1.0, -1.0, 1.0);
			break;
		// = = = = = MID layer = = = = =
		case 9: // LEFT-TOP-MID cubie
			faceColors = new int[] { 0, 2, 3, 0, 0, 0 };
			gl.glTranslated(-1.0, 1.0, 0.0);
			break;
		case 10: // MID-TOP-MID cubie
			faceColors = new int[] { 0, 0, 3, 0, 0, 0 };
			gl.glTranslated(0.0, 1.0, 0.0);
			break;
		case 11: // RIGHT-TOP-MID cubie
			faceColors = new int[] { 1, 0, 3, 0, 0, 0 };
			gl.glTranslated(1.0, 1.0, 0.0);
			break;
		case 12: // LEFT-MID-MID cubie
			faceColors = new int[] { 0, 2, 0, 0, 0, 0 };
			gl.glTranslated(-1.0, 0.0, 0.0);
			break;
		case 13: // MID-MID-MID
			faceColors = new int[] { 1, 2, 3, 4, 5, 6 };
			break;
		case 14: // RIGHT-MID-MID cubie
			faceColors = new int[] { 1, 0, 0, 0, 0, 0 };
			gl.glTranslated(1.0, 0.0, 0.0);
			break;
		case 15: // LEFT-DOWN-MID cubie
			faceColors = new int[] { 0, 2, 0, 4, 0, 0 };
			gl.glTranslated(-1.0, -1.0, 0.0);
			break;
		case 16: // MID-DOWN-MID cubie
			faceColors = new int[] { 0, 0, 0, 4, 0, 0 };
			gl.glTranslated(0.0, -1.0, 0.0);
			break;
		case 17: // RIGHT-DOWN-MID cubie
			faceColors = new int[] { 1, 0, 0, 4, 0, 0 };
			gl.glTranslated(1.0, -1.0, 0.0);
			break;
		// = = = = = BACK layer = = = = =
		case 18: // LEFT-TOP-BACK cubie
			faceColors = new int[] { 0, 2, 3, 0, 0, 6 };
			gl.glTranslated(-1.0, 1.0, -1.0);
			break;
		case 19: // MID-TOP-BACK cubie
			faceColors = new int[] { 0, 0, 3, 0, 0, 6 };
			gl.glTranslated(0.0, 1.0, -1.0);
			break;
		case 20: // RIGHT-TOP-BACK cubie
			faceColors = new int[] { 1, 0, 3, 0, 0, 6 };
			gl.glTranslated(1.0, 1.0, -1.0);
			break;
		case 21: // LEFT-MID-BACK cubie
			faceColors = new int[] { 0, 2, 0, 0, 0, 6 };
			gl.glTranslated(-1.0, 0.0, -1.0);
			break;
		case 22: // MID-MID-BACK cubie
			faceColors = new int[] { 0, 0, 0, 0, 0, 6 };
			gl.glTranslated(0.0, 0.0, -1.0);
			break;
		case 23: // RIGHT-MID-BACK cubie
			faceColors = new int[] { 1, 0, 0, 0, 0, 6 };
			gl.glTranslated(1.0, 0.0, -1.0);
			break;
		case 24: // LEFT-DOWN-BACK cubie
			faceColors = new int[] { 0, 2, 0, 4, 0, 6 };
			gl.glTranslated(-1.0, -1.0, -1.0);
			break;
		case 25: // MID-DOWN-BACK cubie
			faceColors = new int[] { 0, 0, 0, 4, 0, 6 };
			gl.glTranslated(0.0, -1.0, -1.0);
			break;
		case 26: // RIGHT-DOWN-BACK cubie
			faceColors = new int[] { 1, 0, 0, 4, 0, 6 };
			gl.glTranslated(1.0, -1.0, -1.0);
			break;
		}
		gl.glGetDoublev(GLMatrixFunc.GL_MODELVIEW_MATRIX, drawMatrix, 0);
		gl.glPopMatrix();
		this.commit();
	}

	/**
	 * Commits this cubie's draw matrix to it's canonical matrix.
	 */
	public void commit() {
		matrix = Arrays.copyOf(drawMatrix, drawMatrix.length);
	}

	/**
	 * Draw this cubie.
	 * 
	 * @param gl
	 */
	public void draw(GL2 gl) {
		gl.glPushMatrix();
		gl.glMultMatrixd(drawMatrix, 0);

		drawFace(gl, 2, 3, 7, 6, faceColors[0]);// right
		drawFace(gl, 0, 1, 5, 4, faceColors[1]);// left
		drawFace(gl, 1, 2, 6, 5, faceColors[2]);// up
		drawFace(gl, 0, 4, 7, 3, faceColors[3]);// down
		drawFace(gl, 0, 3, 2, 1, faceColors[4]);// front
		drawFace(gl, 4, 5, 6, 7, faceColors[5]);// back

		gl.glPopMatrix();
	}

	private void drawFace(GL2 gl, int a, int b, int c, int d, int color) {
		gl.glColor4dv(colors[color], 0);
		gl.glBegin(GL2.GL_POLYGON);
		gl.glVertex3dv(cubieVerts[a], 0);
		gl.glVertex3dv(cubieVerts[b], 0);
		gl.glVertex3dv(cubieVerts[c], 0);
		gl.glVertex3dv(cubieVerts[d], 0);
		gl.glEnd();

		gl.glColor3dv(colors[0], 0);
		gl.glLineWidth(6.0f);
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glVertex3dv(cubieVerts[a], 0);
		gl.glVertex3dv(cubieVerts[b], 0);
		gl.glVertex3dv(cubieVerts[c], 0);
		gl.glVertex3dv(cubieVerts[d], 0);
		gl.glEnd();
		gl.glLineWidth(1.0f);
	}
}