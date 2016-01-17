/**
 * A class to represent a Rectangle. You do not have to use this, but it's quite
 * convenient.
 *
 * Invariant: right >= left and top >= bottom (i.e., numbers get bigger as you
 * move up/right).
 */
public class Rectangle {
	/**
	 * Left edge of the rectangle.
	 */
	public float left;

	/**
	 * Right edge of the rectangle.
	 */
	public float right;

	/**
	 * Top edge of the rectangle.
	 */
	public float top;

	/**
	 * Bottom edge of the rectangle.
	 */
	public float bottom;

	/**
	 * Population of the Rectangle
	 */
	public int population;

	/**
	 * Create a Rectangle with the given edges.
	 *
	 * @param l left edge
	 * @param r right edge
	 * @param t top edge
	 * @param b bottom edge
	 */
	public Rectangle(float l, float r, float t, float b) {
		left = l;
		right = r;
		top = t;
		bottom = b;
		population = 0;
	}

	/**
	 * Create a Rectangle with the given edges and population
	 *
	 * @param l left edge
	 * @param r right edge
	 * @param t top edge
	 * @param b bottom edge
	 * @param p population
	 *
	 */
	 public Rectangle(float l, float r, float t, float b, int p) {
		left = l;
 		right = r;
 		top = t;
 		bottom = b;
		population = p;
	 }

	/**
	 * Returns a new Rectangle that is the smallest rectangle containing this
	 * and that.
	 *
	 * @param that other Rectangle
	 * @return smallest Rectangle containing this and that
	 */
	public Rectangle encompass(Rectangle that) {
		return new Rectangle(Math.min(this.left, that.left), Math.max(
				this.right, that.right), Math.max(this.top, that.top),
				Math.min(this.bottom, that.bottom));
	}

	/**
	 * Updates the rectangle's population
	 */
	 public void updateRectangle(int i) {
		 population += i;
	 }

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "[left=" + left + " right=" + right + " top=" + top + " bottom="
				+ bottom + "]";
	}
}
