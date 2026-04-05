# Combined Test Case Generator + Fixes for IS442 Auto-Grader
# Generates realistic student submissions with common anomalies and applies necessary fixes
# Generates student submissions that actually attempt to solve exam questions with realistic bugs/anomalies
# Adjusted to be in scripts folder, so the paths are now nested. To use, run this script from the root directory of the project.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_DIR="/tmp/autograder-realistic-tests"
OUTPUT_DIR="$SCRIPT_DIR/is442-project-materials/student-submission"
TEMP_FIX_DIR="/tmp/autograder-fix-temp"

rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Generating Realistic IS442 Test Cases                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo

# Helper function to create data files
create_data_files() {
    local basedir=$1
    # Copy from an existing submission that has the data files
    if [ -f "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" ]; then
        unzip -o -q "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" -d /tmp/data-extract 2>/dev/null
        if [ -d "/tmp/data-extract/ping.lee.2023/Q2" ]; then
            cp /tmp/data-extract/ping.lee.2023/Q2/*.txt "$basedir/" 2>/dev/null || true
            cp /tmp/data-extract/ping.lee.2023/Q2/*.class "$basedir/" 2>/dev/null || true
        fi
        if [ -d "/tmp/data-extract/ping.lee.2023/Q3" ]; then
            cp /tmp/data-extract/ping.lee.2023/Q3/*.class "$basedir/Q3/" 2>/dev/null || true
        fi
    fi
}

# ============================================================================
# Test 1: alice.wong.2024 - EXTRA_NESTING (works despite deep nesting)
# ============================================================================  
echo "[1/12] alice.wong.2024 - EXTRA_NESTING (deep folders but code works)"
BASEDIR="$TEST_DIR/2023-2024-alice.wong.2024/extra/nesting/levels/alice.wong.2024"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Alice Wong
 * Email ID: alice.wong.2024
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for (String word : inputs) {
            Set<Character> seen = new HashSet<>();
            boolean unique = true;
            for (char c : word.toLowerCase().toCharArray()) {
                if (!seen.add(c)) {
                    unique = false;
                    break;
                }
            }
            if (unique) result.add(word);
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Alice Wong
 * Email ID: alice.wong.2024
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for (Object obj : inputs) {
            if (obj instanceof Integer) {
                int num = (Integer) obj;
                if (num % 2 == 0) sum += num;
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Alice Wong
 * Email ID: alice.wong.2024
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            Scanner sc = new Scanner(new File(filename));
            int count = 0;
            double sum = 0;
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split("-");
                String[] names = parts[0].split(" ");
                if (names[0].equalsIgnoreCase(surname) || names[names.length-1].equalsIgnoreCase(surname)) {
                    sum += Double.parseDouble(parts[1]);
                    count++;
                }
            }
            sc.close();
            return count > 0 ? sum / count : 0.0;
        } catch (FileNotFoundException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Alice Wong
 * Email ID: alice.wong.2024
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner sc = new Scanner(new File(filename));
            String top = null;
            double maxGPA = -1;
            boolean found = false;
            while (sc.hasNextLine()) {
                String[] parts = sc.nextLine().split(",");
                for (String course : parts[1].split("-")) {
                    String[] data = course.split("#");
                    if (data[0].equals(courseName)) {
                        found = true;
                        double gpa = Double.parseDouble(data[1]);
                        if (gpa > maxGPA) {
                            maxGPA = gpa;
                            top = parts[0] + "-" + gpa;
                        }
                    }
                }
            }
            sc.close();
            if (!found) throw new DataException();
            return top;
        } catch (FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Alice Wong
 * Email ID: alice.wong.2024
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> result = new ArrayList<>();
        for (Shape s : shapeList) {
            if (s.getArea() <= 1000) result.add(s);
        }
        Collections.sort(result, new ShapeComparator());
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        int areaComp = Double.compare(s2.getArea(), s1.getArea());
        return areaComp != 0 ? areaComp : Double.compare(s2.getPerimeter(), s1.getPerimeter());
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-alice.wong.2024.zip" "2023-2024-alice.wong.2024"

# ============================================================================
# Test 2: bob.tan.2025 - MISSING_QUESTION_FOLDER (Q2 missing entirely)
# ============================================================================
echo "[2/12] bob.tan.2025 - MISSING_QUESTION_FOLDER (forgot Q2)"
BASEDIR="$TEST_DIR/2023-2024-bob.tan.2025"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Bob Tan
 * Email ID: bob.tan.2025
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> ans = new ArrayList<>();
        for(String s : inputs) {
            boolean isIsogram = true;
            for(int i=0; i<s.length(); i++) {
                for(int j=i+1; j<s.length(); j++) {
                    if(Character.toLowerCase(s.charAt(i)) == Character.toLowerCase(s.charAt(j))) {
                        isIsogram = false;
                        break;
                    }
                }
                if(!isIsogram) break;
            }
            if(isIsogram) ans.add(s);
        }
        return ans;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Bob Tan
 * Email ID: bob.tan.2025
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int total = 0;
        for(Object o : inputs) {
            if(o instanceof Integer) {
                Integer i = (Integer)o;
                if(i % 2 == 0) total = total + i;
            }
        }
        return total;
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Bob Tan
 * Email ID: bob.tan.2025
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> filtered = new ArrayList<>();
        for(Shape shape : shapeList) {
            if(shape.getArea() <= 1000) {
                filtered.add(shape);
            }
        }
        filtered.sort(new ShapeComparator());
        return filtered;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape a, Shape b) {
        if(b.getArea() > a.getArea()) return 1;
        if(b.getArea() < a.getArea()) return -1;
        if(b.getPerimeter() > a.getPerimeter()) return 1;
        if(b.getPerimeter() < a.getPerimeter()) return -1;
        return 0;
    }
}
JAVA

create_data_files "$BASEDIR/Q3"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-bob.tan.2025.zip" "2023-2024-bob.tan.2025"

# ============================================================================
# Test 3: charlie.lim.2023 - MISSING_HEADER (no headers in any files)
# ============================================================================
echo "[3/12] charlie.lim.2023 - MISSING_HEADER (forgot all headers)"
BASEDIR="$TEST_DIR/2023-2024-charlie.lim.2023"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> res = new ArrayList<>();
        for(String w : inputs) {
            String lower = w.toLowerCase();
            boolean ok = true;
            for(int i=0; i<lower.length()-1; i++) {
                if(lower.substring(i+1).contains(lower.substring(i, i+1))) {
                    ok = false;
                    break;
                }
            }
            if(ok) res.add(w);
        }
        return res;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for(int i=0; i<inputs.size(); i++) {
            Object obj = inputs.get(i);
            if(obj.getClass().equals(Integer.class)) {
                int val = (int)obj;
                if(val % 2 == 0) sum += val;
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            File f = new File(filename);
            Scanner s = new Scanner(f);
            ArrayList<Double> ages = new ArrayList<>();
            while(s.hasNext()) {
                String line = s.nextLine();
                String[] arr = line.split("-");
                String name = arr[0];
                double age = Double.valueOf(arr[1]);
                String[] nameParts = name.split(" ");
                String first = nameParts[0];
                String last = nameParts[nameParts.length - 1];
                if(first.equalsIgnoreCase(surname) || last.equalsIgnoreCase(surname)) {
                    ages.add(age);
                }
            }
            s.close();
            if(ages.size() == 0) return 0.0;
            double total = 0;
            for(double a : ages) total += a;
            return total / ages.size();
        } catch(Exception e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner scan = new Scanner(new File(filename));
            String bestStudent = "";
            double bestGPA = 0;
            boolean courseExists = false;
            while(scan.hasNextLine()) {
                String line = scan.nextLine();
                String[] split = line.split(",");
                String studentName = split[0];
                String[] courses = split[1].split("-");
                for(String c : courses) {
                    String[] courseInfo = c.split("#");
                    if(courseInfo[0].equals(courseName)) {
                        courseExists = true;
                        double gpa = Double.parseDouble(courseInfo[1]);
                        if(gpa > bestGPA) {
                            bestGPA = gpa;
                            bestStudent = studentName + "-" + gpa;
                        }
                    }
                }
            }
            scan.close();
            if(!courseExists) throw new DataException();
            return bestStudent;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        ArrayList<Shape> list = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000) list.add(s);
        }
        list.sort(new ShapeComparator());
        return list;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape x, Shape y) {
        double areaDiff = y.getArea() - x.getArea();
        if(areaDiff != 0) return (int)(areaDiff * 1000);
        double periDiff = y.getPerimeter() - x.getPerimeter();
        return (int)(periDiff * 1000);
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-charlie.lim.2023.zip" "2023-2024-charlie.lim.2023"

# ============================================================================
# Test 4: diana.ng.2022 - INCOMPLETE_HEADER (various missing Name/Email)
# ============================================================================
echo "[4/12] diana.ng.2022 - INCOMPLETE_HEADER (partial headers)"
BASEDIR="$TEST_DIR/2023-2024-diana.ng.2022"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Diana Ng
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> output = new ArrayList<>();
        for(String word : inputs) {
            HashSet<Character> set = new HashSet<>();
            boolean duplicate = false;
            for(char ch : word.toLowerCase().toCharArray()) {
                if(set.contains(ch)) {
                    duplicate = true;
                    break;
                }
                set.add(ch);
            }
            if(!duplicate) output.add(word);
        }
        return output;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Email ID: diana.ng.2022
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int result = 0;
        for(Object item : inputs) {
            try {
                if(item instanceof Integer) {
                    int n = (Integer) item;
                    if(n % 2 == 0) result += n;
                }
            } catch(Exception e) {}
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: 
 * Email ID: diana.ng.2022
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        double sum = 0;
        int count = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            while((line = br.readLine()) != null) {
                String[] tokens = line.split("-");
                String fullName = tokens[0];
                double age = Double.parseDouble(tokens[1]);
                String[] nameParts = fullName.trim().split("\\s+");
                if(nameParts[0].equalsIgnoreCase(surname) || 
                   nameParts[nameParts.length-1].equalsIgnoreCase(surname)) {
                    sum += age;
                    count++;
                }
            }
            br.close();
            return count == 0 ? 0.0 : sum / count;
        } catch(IOException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Diana Ng
 * Email ID: 
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        String topStudent = null;
        double highestGPA = -1.0;
        boolean foundCourse = false;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            String line;
            while((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String name = parts[0];
                String[] enrollments = parts[1].split("-");
                for(String enrollment : enrollments) {
                    String[] courseData = enrollment.split("#");
                    if(courseData[0].equals(courseName)) {
                        foundCourse = true;
                        double gpa = Double.parseDouble(courseData[1]);
                        if(gpa > highestGPA) {
                            highestGPA = gpa;
                            topStudent = name + "-" + gpa;
                        }
                    }
                }
            }
            reader.close();
            if(!foundCourse) throw new DataException();
            return topStudent;
        } catch(FileNotFoundException e) {
            throw new DataException();
        } catch(IOException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Diana Ng
 * Email ID: diana.ng.2022
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> valid = new ArrayList<>();
        for(Shape shape : shapeList) {
            if(shape.getArea() <= 1000) valid.add(shape);
        }
        Collections.sort(valid, new ShapeComparator());
        return valid;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    @Override
    public int compare(Shape s1, Shape s2) {
        int areaComparison = Double.compare(s2.getArea(), s1.getArea());
        if(areaComparison != 0) return areaComparison;
        return Double.compare(s2.getPerimeter(), s1.getPerimeter());
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-diana.ng.2022.zip" "2023-2024-diana.ng.2022"

# ============================================================================
# Test 5: evan.koh.2024 - COMPILATION_ERROR (syntax errors)
# ============================================================================
echo "[5/12] evan.koh.2024 - COMPILATION_ERROR (syntax mistakes)"
BASEDIR="$TEST_DIR/2023-2024-evan.koh.2024"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Evan Koh
 * Email ID: evan.koh.2024
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> answer = new ArrayList<>();
        for(String s : inputs) {
            boolean isUnique = true;
            String lower = s.toLowerCase();
            for(int i = 0; i < lower.length(); i++) {
                for(int j = i+1; j < lower.length(); j++) {
                    if(lower.charAt(i) == lower.charAt(j)) {
                        isUnique = false;
                        break;
                    }
                }
            }
            if(isUnique) {
                answer.add(s);
            }
        return answer;  // Missing closing brace for method!
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Evan Koh
 * Email ID: evan.koh.2024
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for(Object o : inputs) {
            if(o instanceof Integer) {
                int num = (Integer) o;
                if(num % 2 == 0) {
                    String debug = "adding " + num;  // Unclosed string!
                    sum += num;
                }
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Evan Koh
 * Email ID: evan.koh.2024
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            Scanner sc = new Scanner(new File(filename));
            ArrayList<Double> matchingAges = new ArrayList<>();
            while(sc.hasNextLine()) {
                String data = sc.nextLine();
                String[] info = data.split("-");
                String personName = info[0];
                double personAge = Double.parseDouble(info[1]);
                String[] names = personName.split(" ");
                if(names[0].equalsIgnoreCase(surname) || names[names.length-1].equalsIgnoreCase(surname)) {
                    matchingAges.add(personAge);
                }
            }
            sc.close();
            if(matchingAges.isEmpty()) return 0.0;
            double total = 0.0;
            for(double age : matchingAges) total += age;
            return total / matchingAges.size();
        } catch(FileNotFoundException ex) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Evan Koh
 * Email ID: evan.koh.2024
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner sc = new Scanner(new File(filename));
            String bestName = "";
            double bestScore = 0.0;
            boolean exists = false;
            while(sc.hasNextLine()) {
                String record = sc.nextLine();
                String[] fields = record.split(",");
                String student = fields[0];
                String[] courseList = fields[1].split("-");
                for(String course : courseList) {
                    String[] details = course.split("#");
                    if(details[0].equals(courseName)) {
                        exists = true;
                        double score = Double.parseDouble(details[1]);
                        if(score > bestScore) {
                            bestScore = score;
                            bestName = student + "-" + score;
                        }
                    }
                }
            }
            sc.close();
            if(!exists) throw new DataException();
            return bestName;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Evan Koh
 * Email ID: evan.koh.2024
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> result = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000) result.add(s);
        }
        result.sort(new ShapeComparator());
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        if(s1.getArea() != s2.getArea()) {
            return s2.getArea() > s1.getArea() ? 1 : -1;
        }
        return s2.getPerimeter() > s1.getPerimeter() ? 1 : -1;
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-evan.koh.2024.zip" "2023-2024-evan.koh.2024"

# ============================================================================
# Test 6: fiona.chen.2023 - EXECUTION_TIMEOUT (infinite loop in Q1a)
# ============================================================================
echo "[6/12] fiona.chen.2023 - EXECUTION_TIMEOUT (**CRITICAL** infinite loop)"
BASEDIR="$TEST_DIR/2023-2024-fiona.chen.2023"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Fiona Chen
 * Email ID: fiona.chen.2023
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for(String word : inputs) {
            boolean isIsogram = true;
            int i = 0;
            while(i < word.length()) {  // BUG: forgot to increment i - INFINITE LOOP!
                char c1 = Character.toLowerCase(word.charAt(i));
                for(int j = i+1; j < word.length(); j++) {
                    char c2 = Character.toLowerCase(word.charAt(j));
                    if(c1 == c2) {
                        isIsogram = false;
                        break;
                    }
                }
                // MISSING: i++  <- This causes infinite loop!
            }
            if(isIsogram) result.add(word);
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Fiona Chen
 * Email ID: fiona.chen.2023
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for(Object obj : inputs) {
            if(obj instanceof Integer) {
                Integer num = (Integer) obj;
                if(num.intValue() % 2 == 0) {
                    sum += num.intValue();
                }
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Fiona Chen
 * Email ID: fiona.chen.2023
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            File file = new File(filename);
            Scanner scanner = new Scanner(file);
            double sumAges = 0;
            int peopleCount = 0;
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] parts = line.split("-");
                double age = Double.parseDouble(parts[1]);
                String[] nameTokens = parts[0].split(" ");
                boolean matches = false;
                for(String token : nameTokens) {
                    if(token.equalsIgnoreCase(surname)) matches = true;
                }
                if(matches) {
                    sumAges += age;
                    peopleCount++;
                }
            }
            scanner.close();
            if(peopleCount == 0) return 0.0;
            return sumAges / peopleCount;
        } catch(FileNotFoundException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Fiona Chen
 * Email ID: fiona.chen.2023
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            File file = new File(filename);
            Scanner scanner = new Scanner(file);
            double maxGPA = -1;
            String winner = null;
            boolean courseFound = false;
            while(scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] tokens = line.split(",");
                String name = tokens[0];
                String[] courses = tokens[1].split("-");
                for(String courseStr : courses) {
                    String[] courseInfo = courseStr.split("#");
                    String cName = courseInfo[0];
                    double gpa = Double.parseDouble(courseInfo[1]);
                    if(cName.equals(courseName)) {
                        courseFound = true;
                        if(gpa > maxGPA) {
                            maxGPA = gpa;
                            winner = name + "-" + gpa;
                        }
                    }
                }
            }
            scanner.close();
            if(!courseFound) throw new DataException();
            return winner;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Fiona Chen
 * Email ID: fiona.chen.2023
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> filtered = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000) filtered.add(s);
        }
        Collections.sort(filtered, new ShapeComparator());
        return filtered;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape a, Shape b) {
        double areaDiff = b.getArea() - a.getArea();
        if(Math.abs(areaDiff) > 0.001) {
            return areaDiff > 0 ? 1 : -1;
        }
        double periDiff = b.getPerimeter() - a.getPerimeter();
        return periDiff > 0 ? 1 : -1;
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-fiona.chen.2023.zip" "2023-2024-fiona.chen.2023"

# ============================================================================
# Test 7: george.low.2025 - RUNTIME_ERROR (throws exceptions)
# ============================================================================  
echo "[7/12] george.low.2025 - RUNTIME_ERROR (exceptions in code)"
BASEDIR="$TEST_DIR/2023-2024-george.low.2025"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: George Low
 * Email ID: george.low.2025
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> results = new ArrayList<>();
        for(String word : inputs) {
            Set<Character> chars = new HashSet<>();
            for(int i = 0; i < word.length(); i++) {
                char c = Character.toLowerCase(word.charAt(i));
                if(chars.contains(c)) {
                    throw new RuntimeException("Duplicate found: " + c); // BUG: Throws on finding duplicate!
                }
                chars.add(c);
            }
            results.add(word);
        }
        return results;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: George Low
 * Email ID: george.low.2025
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int[] numbers = new int[0];  // BUG: Empty array
        int sum = 0;
        int idx = 0;
        for(Object obj : inputs) {
            if(obj instanceof Integer) {
                int num = (Integer) obj;
                numbers[idx] = num;  // ArrayIndexOutOfBoundsException!
                if(num % 2 == 0) sum += num;
                idx++;
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: George Low
 * Email ID: george.low.2025
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            Scanner sc = new Scanner(new File(filename));
            List<Double> ages = new ArrayList<>();
            while(sc.hasNext()) {
                String line = sc.nextLine();
                String[] data = line.split("-");
                String[] nameParts = data[0].split(" ");
                double age = Double.parseDouble(data[1]);
                if(nameParts[0].equalsIgnoreCase(surname) || nameParts[nameParts.length-1].equalsIgnoreCase(surname)) {
                    ages.add(age);
                }
            }
            sc.close();
            if(ages.isEmpty()) return 0.0;
            double total = 0;
            for(double a : ages) total += a;
            return total / ages.size();
        } catch(FileNotFoundException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: George Low
 * Email ID: george.low.2025
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner sc = new Scanner(new File(filename));
            String topName = "";
            double topGPA = 0;
            boolean found = false;
            while(sc.hasNext()) {
                String line = sc.nextLine();
                String[] parts = line.split(",");
                String student = parts[0];
                String[] courses = parts[1].split("-");
                for(String c : courses) {
                    String[] info = c.split("#");
                    if(info[0].equals(courseName)) {
                        found = true;
                        double gpa = Double.parseDouble(info[1]);
                        if(gpa > topGPA) {
                            topGPA = gpa;
                            topName = student + "-" + gpa;
                        }
                    }
                }
            }
            sc.close();
            if(!found) throw new DataException();
            return topName;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: George Low
 * Email ID: george.low.2025
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> output = new ArrayList<>();
        for(Shape shape : shapeList) {
            if(shape.getArea() <= 1000) {
                output.add(shape);
            }
        }
        output.sort(new ShapeComparator());
        return output;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        return s1.getArea() < s2.getArea() ? 1 : 
               s1.getArea() > s2.getArea() ? -1 :
               s1.getPerimeter() < s2.getPerimeter() ? 1 :
               s1.getPerimeter() > s2.getPerimeter() ? -1 : 0;
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-george.low.2025.zip" "2023-2024-george.low.2025"

# ============================================================================
# Test 8: harny.goh.2023 - SPELLING_ERROR (typo in folder name)
# ============================================================================
echo "[8/12] harny.goh.2023 - SPELLING_ERROR (folder name typo)"
BASEDIR="$TEST_DIR/2023-2024-harny.goh.2023"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

# Folder name has typo "harny" but headers are correct "harry.goh.2023"
cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Harry Goh
 * Email ID: harry.goh.2023
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> ans = new ArrayList<>();
        for(String str : inputs) {
            HashMap<Character, Integer> freq = new HashMap<>();
            boolean valid = true;
            for(char c : str.toLowerCase().toCharArray()) {
                freq.put(c, freq.getOrDefault(c, 0) + 1);
                if(freq.get(c) > 1) {
                    valid = false;
                    break;
                }
            }
            if(valid) ans.add(str);
        }
        return ans;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Harry Goh
 * Email ID: harry.goh.2023
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int answer = 0;
        for(Object o: inputs) {
            if(o instanceof Integer) {
                int val = ((Integer)o).intValue();
                if(val % 2 == 0) answer = answer + val;
            }
        }
        return answer;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Harry Goh
 * Email ID: harry.goh.2023
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            Scanner s = new Scanner(new File(filename));
            ArrayList<Integer> validAges = new ArrayList<>();
            while(s.hasNextLine()) {
                String row = s.nextLine();
                String[] cols = row.split("-");
                int age = Integer.parseInt(cols[1]);
                String fullName = cols[0];
                String[] parts = fullName.split(" ");
                if(parts[0].toUpperCase().equals(surname.toUpperCase()) || 
                   parts[parts.length-1].toUpperCase().equals(surname.toUpperCase())) {
                    validAges.add(age);
                }
            }
            s.close();
            if(validAges.size() == 0) return 0.0;
            double sum = 0;
            for(int a : validAges) sum += a;
            return sum / validAges.size();
        } catch(FileNotFoundException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Harry Goh
 * Email ID: harry.goh.2023
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner s = new Scanner(new File(filename));
            double maxScore = -1;
            String maxStudent = null;
            boolean exists = false;
            while(s.hasNextLine()) {
                String row = s.nextLine();
                String[] cols = row.split(",");
                String studentName = cols[0];
                String[] classes = cols[1].split("-");
                for(String cls : classes) {
                    String[] classData = cls.split("#");
                    if(classData[0].equals(courseName)) {
                        exists = true;
                        double score = Double.parseDouble(classData[1]);
                        if(score > maxScore) {
                            maxScore = score;
                            maxStudent = studentName + "-" + score;
                        }
                    }
                }
            }
            s.close();
            if(!exists) throw new DataException();
            return maxStudent;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Harry Goh
 * Email ID: harry.goh.2023
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        ArrayList<Shape> temp = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000.0) temp.add(s);
        }
        temp.sort(new ShapeComparator());
        return temp;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape a, Shape b) {
        int cmp = Double.compare(b.getArea(), a.getArea());
        if(cmp == 0) cmp = Double.compare(b.getPerimeter(), a.getPerimeter());
        return cmp;
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-harny.goh.2023.zip" "2023-2024-harny.goh.2023"

# ============================================================================
# Test 9: iris.lee.2022 - FOLDER_NOT_RENAMED (RenameToYourUsername variant)
# ============================================================================
echo "[9/12] iris.lee.2022 - FOLDER_NOT_RENAMED (RenameToYourUsername)"
BASEDIR="$TEST_DIR/2023-2024-iris.lee.2022/RenameToYourUsername"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Iris Lee
 * Email ID: iris.lee.2022
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for(String word : inputs) {
            boolean[] seen = new boolean[26];
            boolean isIsogram = true;
            for(char ch : word.toLowerCase().toCharArray()) {
                int idx = ch - 'a';
                if(seen[idx]) {
                    isIsogram = false;
                    break;
                }
                seen[idx] = true;
            }
            if(isIsogram) result.add(word);
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Iris Lee
 * Email ID: iris.lee.2022
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int total = 0;
        for(int i=0; i<inputs.size(); i++) {
            if(inputs.get(i) instanceof Integer) {
                int value = (Integer)inputs.get(i);
                if(value % 2 == 0) total += value;
            }
        }
        return total;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Iris Lee
 * Email ID: iris.lee.2022
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            int count = 0;
            double sum = 0;
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split("-");
                String name = parts[0].trim();
                double age = Double.parseDouble(parts[1].trim());
                String[] words = name.split("\\s+");
                if(words[0].equalsIgnoreCase(surname) || words[words.length-1].equalsIgnoreCase(surname)) {
                    sum += age;
                    count++;
                }
            }
            br.close();
            return count > 0 ? sum / count : 0.0;
        } catch(IOException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Iris Lee
 * Email ID: iris.lee.2022
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String line;
            String bestStudent = "";
            double bestGPA = -999;
            boolean foundCourse = false;
            while((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String name = parts[0];
                String[] courses = parts[1].split("-");
                for(String course : courses) {
                    String[] details = course.split("#");
                    if(details[0].equals(courseName)) {
                        foundCourse = true;
                        double gpa = Double.parseDouble(details[1]);
                        if(gpa > bestGPA) {
                            bestGPA = gpa;
                            bestStudent = name + "-" + gpa;
                        }
                    }
                }
            }
            br.close();
            if(!foundCourse) throw new DataException();
            return bestStudent;
        } catch(IOException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Iris Lee
 * Email ID: iris.lee.2022
 */
import java.util.*;
import java.util.stream.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        return shapeList.stream()
            .filter(s -> s.getArea() <= 1000)
            .sorted(new ShapeComparator())
            .collect(Collectors.toList());
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        return Comparator.comparingDouble(Shape::getArea).reversed()
            .thenComparing(Comparator.comparingDouble(Shape::getPerimeter).reversed())
            .compare(s1, s2);
    }
}
JAVA

create_data_files "$BASEDIR/Q2"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-iris.lee.2022.zip" "2023-2024-iris.lee.2022"

# ============================================================================
# Test 10: john.smith.2023 - MIXED_ANOMALIES (everything wrong!)
# ============================================================================
echo "[10/12] john.smith.2023 - MIXED_ANOMALIES (multiple issues)"
BASEDIR="$TEST_DIR/2023-2024-john.smith.2023/RenameMe"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q3"  # Q2 missing!

# Q1a: No header + Compilation error
cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> answer = new ArrayList<>();
        for(String w : inputs) {
            String err = "unclosed string
            boolean ok = true;
            for(int i=0; i<w.length(); i++) {
                for(int j=i+1; j<w.length(); j++) {
                    if(Character.toLowerCase(w.charAt(i)) == Character.toLowerCase(w.charAt(j))) {
                        ok = false;
                    }
                }
            }
            if(ok) answer.add(w);
        }
        return answer;
    }
}
JAVA

# Q1b missing entirely!

# Q3: Incomplete header + infinite loop
cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: 
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> result = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000) result.add(s);
        }
        // BUG: Infinite loop due to wrong comparator logic
        boolean sorted = false;
        while(!sorted) {
            sorted = true;
            // Forgot to actually sort or break!
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        return 0; // Wrong: always returns 0!
    }
}
JAVA

create_data_files "$BASEDIR/Q3"
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-john.smith.2023.zip" "2023-2024-john.smith.2023"

# ============================================================================
# Test 11: kelly.ng.2024 - MISSING_Q2_DEPENDENCIES
# ============================================================================
echo "[11/12] kelly.ng.2024 - MISSING_Q2_DEPENDENCIES (Q2 deps missing)"
BASEDIR="$TEST_DIR/2023-2024-kelly.ng.2024/kelly.ng.2024"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Kelly Ng
 * Email ID: kelly.ng.2024
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> result = new ArrayList<>();
        for(String word : inputs) {
            Set<Character> chars = new HashSet<>();
            boolean valid = true;
            for(char c : word.toLowerCase().toCharArray()) {
                if(!chars.add(c)) {
                    valid = false;
                    break;
                }
            }
            if(valid) result.add(word);
        }
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Kelly Ng
 * Email ID: kelly.ng.2024
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int sum = 0;
        for(Object obj : inputs) {
            if(obj instanceof Integer) {
                int n = (Integer) obj;
                if(n % 2 == 0) sum += n;
            }
        }
        return sum;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Kelly Ng
 * Email ID: kelly.ng.2024
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            Scanner sc = new Scanner(new File(filename));
            List<Double> ages = new ArrayList<>();
            while(sc.hasNextLine()) {
                String[] parts = sc.nextLine().split("-");
                String[] names = parts[0].split(" ");
                double age = Double.parseDouble(parts[1]);
                if(names[0].equalsIgnoreCase(surname) || names[names.length-1].equalsIgnoreCase(surname)) {
                    ages.add(age);
                }
            }
            sc.close();
            if(ages.isEmpty()) return 0.0;
            double sum = 0;
            for(double a : ages) sum += a;
            return sum / ages.size();
        } catch(Exception e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Kelly Ng
 * Email ID: kelly.ng.2024
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            Scanner sc = new Scanner(new File(filename));
            String topStudent = null;
            double maxGPA = -1;
            boolean found = false;
            while(sc.hasNextLine()) {
                String[] parts = sc.nextLine().split(",");
                String[] courses = parts[1].split("-");
                for(String course : courses) {
                    String[] info = course.split("#");
                    if(info[0].equals(courseName)) {
                        found = true;
                        double gpa = Double.parseDouble(info[1]);
                        if(gpa > maxGPA) {
                            maxGPA = gpa;
                            topStudent = parts[0] + "-" + gpa;
                        }
                    }
                }
            }
            sc.close();
            if(!found) throw new DataException();
            return topStudent;
        } catch(FileNotFoundException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Kelly Ng
 * Email ID: kelly.ng.2024
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> filtered = new ArrayList<>();
        for(Shape s : shapeList) {
            if(s.getArea() <= 1000) filtered.add(s);
        }
        Collections.sort(filtered, new ShapeComparator());
        return filtered;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    public int compare(Shape s1, Shape s2) {
        int areaComp = Double.compare(s2.getArea(), s1.getArea());
        if(areaComp != 0) return areaComp;
        return Double.compare(s2.getPerimeter(), s1.getPerimeter());
    }
}
JAVA

# NOTE: Intentionally NOT calling create_data_files for Q2 - missing dependencies!
# Only add Q3 dependencies
if [ -f "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" ]; then
    unzip -o -q "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" -d /tmp/data-extract 2>/dev/null
    if [ -d "/tmp/data-extract/ping.lee.2023/Q3" ]; then
        cp /tmp/data-extract/ping.lee.2023/Q3/*.class "$BASEDIR/Q3/" 2>/dev/null || true
    fi
fi
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-kelly.ng.2024.zip" "2023-2024-kelly.ng.2024"

# ============================================================================
# Test 12: leo.tan.2025 - MISSING_Q3_DEPENDENCIES
# ============================================================================
echo "[12/12] leo.tan.2025 - MISSING_Q3_DEPENDENCIES (Q3 deps missing)"
BASEDIR="$TEST_DIR/2023-2024-leo.tan.2025/leo.tan.2025"
mkdir -p "$BASEDIR/Q1" "$BASEDIR/Q2" "$BASEDIR/Q3"

cat > "$BASEDIR/Q1/Q1a.java" << 'JAVA'
/*
 * Name: Leo Tan
 * Email ID: leo.tan.2025
 */
import java.util.*;
public class Q1a {
    public static ArrayList<String> getIsogramWords(ArrayList<String> inputs) {
        ArrayList<String> ans = new ArrayList<>();
        for(String s : inputs) {
            Map<Character, Integer> freq = new HashMap<>();
            boolean isIsogram = true;
            for(char c : s.toLowerCase().toCharArray()) {
                freq.put(c, freq.getOrDefault(c, 0) + 1);
                if(freq.get(c) > 1) {
                    isIsogram = false;
                    break;
                }
            }
            if(isIsogram) ans.add(s);
        }
        return ans;
    }
}
JAVA

cat > "$BASEDIR/Q1/Q1b.java" << 'JAVA'
/*
 * Name: Leo Tan
 * Email ID: leo.tan.2025
 */
import java.util.*;
public class Q1b {
    public static int getSumOfEvenIntegers(ArrayList<Object> inputs) {
        int total = 0;
        for(Object o : inputs) {
            if(o instanceof Integer) {
                int val = ((Integer) o).intValue();
                if(val % 2 == 0) total += val;
            }
        }
        return total;
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2a.java" << 'JAVA'
/*
 * Name: Leo Tan
 * Email ID: leo.tan.2025
 */
import java.io.*;
import java.util.*;
public class Q2a {
    public static double getAverageAge(String filename, String surname) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            List<Double> matchingAges = new ArrayList<>();
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split("-");
                String[] nameParts = parts[0].split(" ");
                double age = Double.parseDouble(parts[1]);
                if(nameParts[0].equalsIgnoreCase(surname) || nameParts[nameParts.length-1].equalsIgnoreCase(surname)) {
                    matchingAges.add(age);
                }
            }
            br.close();
            if(matchingAges.isEmpty()) return 0.0;
            double sum = 0;
            for(double a : matchingAges) sum += a;
            return sum / matchingAges.size();
        } catch(IOException e) {
            return -1.0;
        }
    }
}
JAVA

cat > "$BASEDIR/Q2/Q2b.java" << 'JAVA'
/*
 * Name: Leo Tan
 * Email ID: leo.tan.2025
 */
import java.io.*;
import java.util.*;
public class Q2b {
    public static String getTopStudent(String filename, String courseName) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            String best = null;
            double bestGPA = -1;
            boolean foundCourse = false;
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String[] courses = parts[1].split("-");
                for(String c : courses) {
                    String[] courseData = c.split("#");
                    if(courseData[0].equals(courseName)) {
                        foundCourse = true;
                        double gpa = Double.parseDouble(courseData[1]);
                        if(gpa > bestGPA) {
                            bestGPA = gpa;
                            best = parts[0] + "-" + gpa;
                        }
                    }
                }
            }
            br.close();
            if(!foundCourse) throw new DataException();
            return best;
        } catch(IOException e) {
            throw new DataException();
        }
    }
}
JAVA

cat > "$BASEDIR/Q3/Q3.java" << 'JAVA'
/*
 * Name: Leo Tan
 * Email ID: leo.tan.2025
 */
import java.util.*;
public class Q3 {
    public static List<Shape> sortShapes(List<Shape> shapeList) {
        List<Shape> result = new ArrayList<>();
        for(Shape shape : shapeList) {
            if(shape.getArea() <= 1000) {
                result.add(shape);
            }
        }
        Collections.sort(result, new ShapeComparator());
        return result;
    }
}
JAVA

cat > "$BASEDIR/Q3/ShapeComparator.java" << 'JAVA'
import java.util.Comparator;
public class ShapeComparator implements Comparator<Shape> {
    @Override
    public int compare(Shape s1, Shape s2) {
        int areaResult = Double.compare(s2.getArea(), s1.getArea());
        if(areaResult != 0) return areaResult;
        return Double.compare(s2.getPerimeter(), s1.getPerimeter());
    }
}
JAVA

# Add Q2 dependencies but NOT Q3 dependencies (intentionally missing)
if [ -f "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" ]; then
    unzip -o -q "$SCRIPT_DIR/is442-project-materials/student-submission/2023-2024-ping.lee.2023.zip" -d /tmp/data-extract 2>/dev/null
    if [ -d "/tmp/data-extract/ping.lee.2023/Q2" ]; then
        cp /tmp/data-extract/ping.lee.2023/Q2/*.txt "$BASEDIR/Q2/" 2>/dev/null || true
        cp /tmp/data-extract/ping.lee.2023/Q2/*.class "$BASEDIR/Q2/" 2>/dev/null || true
    fi
fi
cd "$TEST_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-leo.tan.2025.zip" "2023-2024-leo.tan.2025"

echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✓ Successfully Generated 12 Realistic Test Cases           ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "Test cases created:"
ls -1 "$OUTPUT_DIR" | grep -E "(alice|bob|charlie|diana|evan|fiona|george|harry|iris|john|kelly|leo)" | nl
echo
echo "All test cases saved to:"
echo "  $OUTPUT_DIR"
echo

# ═══════════════════════════════════════════════════════════════
# FIXING GENERATED TEST CASES
# ═══════════════════════════════════════════════════════════════
echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Applying Fixes to Generated Submissions                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo

# Source for required class files
TEMPLATE_DIR="$SCRIPT_DIR/is442-project-materials/RenameToYourUsername"

# Extract each generated ZIP, fix issues, and repack
for student in alice.wong.2024 charlie.lim.2023 diana.ng.2022 evan.koh.2024 fiona.chen.2023 george.low.2025 harny.goh.2023 iris.lee.2022 kelly.ng.2024 leo.tan.2025; do
    echo "Fixing $student..."
    rm -rf "$TEMP_FIX_DIR"
    mkdir -p "$TEMP_FIX_DIR"
    
    # Extract
    unzip -q "$OUTPUT_DIR/2023-2024-$student.zip" -d "$TEMP_FIX_DIR"
    
    # Find all Q2 folders and copy required files
    find "$TEMP_FIX_DIR" -type d -name "Q2" | while read q2dir; do
        cp "$TEMPLATE_DIR/Q2/DataException.class" "$q2dir/" 2>/dev/null || true
        cp "$TEMPLATE_DIR/Q2"/*.txt "$q2dir/" 2>/dev/null || true
    done
    
    # Find all Q3 folders and copy required Shape class files
    find "$TEMP_FIX_DIR" -type d -name "Q3" | while read q3dir; do
        cp "$TEMPLATE_DIR/Q3/Shape.class" "$q3dir/" 2>/dev/null || true
        cp "$TEMPLATE_DIR/Q3/Circle.class" "$q3dir/" 2>/dev/null || true
        cp "$TEMPLATE_DIR/Q3/Rectangle.class" "$q3dir/" 2>/dev/null || true
    done
    
    # Repack only this student's folder
    cd "$TEMP_FIX_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-$student.zip" "2023-2024-$student"
done

# Handle bob.tan (has Q1 and Q3 but NO Q2)
echo "Fixing bob.tan.2025..."
rm -rf "$TEMP_FIX_DIR"
mkdir -p "$TEMP_FIX_DIR"
unzip -q "$OUTPUT_DIR/2023-2024-bob.tan.2025.zip" -d "$TEMP_FIX_DIR"
find "$TEMP_FIX_DIR" -type d -name "Q3" | while read q3dir; do
    cp "$TEMPLATE_DIR/Q3/Shape.class" "$q3dir/" 2>/dev/null || true
    cp "$TEMPLATE_DIR/Q3/Circle.class" "$q3dir/" 2>/dev/null || true
    cp "$TEMPLATE_DIR/Q3/Rectangle.class" "$q3dir/" 2>/dev/null || true
done
cd "$TEMP_FIX_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-bob.tan.2025.zip" "2023-2024-bob.tan.2025"

# Handle john.smith (RenameMe folder, has Q1 and Q3 but NO Q2)
echo "Fixing john.smith.2023..."
rm -rf "$TEMP_FIX_DIR"
mkdir -p "$TEMP_FIX_DIR"
unzip -q "$OUTPUT_DIR/2023-2024-john.smith.2023.zip" -d "$TEMP_FIX_DIR"
find "$TEMP_FIX_DIR" -type d -name "Q3" | while read q3dir; do
    cp "$TEMPLATE_DIR/Q3/Shape.class" "$q3dir/" 2>/dev/null || true
    cp "$TEMPLATE_DIR/Q3/Circle.class" "$q3dir/" 2>/dev/null || true
    cp "$TEMPLATE_DIR/Q3/Rectangle.class" "$q3dir/" 2>/dev/null || true
done
cd "$TEMP_FIX_DIR" && zip -qr "$OUTPUT_DIR/2023-2024-john.smith.2023.zip" *

echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅ Setup Complete!                                          ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "Final student submissions are ready in:"
echo "  $OUTPUT_DIR"
echo