package com.mathwise.content.config;

import com.mathwise.common.entity.Exercise;
import com.mathwise.common.entity.KnowledgeNode;
import com.mathwise.common.entity.Lesson;
import com.mathwise.common.repository.ExerciseRepository;
import com.mathwise.common.repository.KnowledgeNodeRepository;
import com.mathwise.common.repository.LessonRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements ApplicationRunner {

    private final KnowledgeNodeRepository knowledgeNodeRepository;
    private final ExerciseRepository exerciseRepository;
    private final LessonRepository lessonRepository;

    public DataSeeder(KnowledgeNodeRepository knowledgeNodeRepository,
                       ExerciseRepository exerciseRepository,
                       LessonRepository lessonRepository) {
        this.knowledgeNodeRepository = knowledgeNodeRepository;
        this.exerciseRepository = exerciseRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedKnowledgeNodesAndExercises();
        seedLessons();
    }

    // ---------------- KNOWLEDGE NODES + EXERCISES ----------------

    private void seedKnowledgeNodesAndExercises() {
        if (knowledgeNodeRepository.count() > 0) return;

        KnowledgeNode addition = node("ARITH_ADDITION", "Addition", 1, null);
        KnowledgeNode subtraction = node("ARITH_SUBTRACTION", "Subtraction", 1, addition);
        KnowledgeNode multiplication = node("ARITH_MULTIPLICATION", "Multiplication", 2, addition);
        KnowledgeNode division = node("ARITH_DIVISION", "Division", 2, multiplication);
        KnowledgeNode fractionsSimplify = node("FRACTIONS_SIMPLIFY", "Simplifying Fractions", 3, division);
        KnowledgeNode fractionsAddSub = node("FRACTIONS_ADD_SUB", "Adding & Subtracting Fractions", 3, fractionsSimplify);
        KnowledgeNode algebraLinear = node("ALGEBRA_LINEAR", "Linear Equations", 4, subtraction);
        KnowledgeNode algebraFactorize = node("ALGEBRA_FACTORIZE", "Factorization", 5, algebraLinear);

        knowledgeNodeRepository.saveAll(List.of(
                addition, subtraction, multiplication, division,
                fractionsSimplify, fractionsAddSub, algebraLinear, algebraFactorize
        ));

        seedExercises(addition, subtraction, multiplication, division,
                fractionsSimplify, fractionsAddSub, algebraLinear, algebraFactorize);
    }

    private KnowledgeNode node(String code, String title, int difficulty, KnowledgeNode prereq) {
        KnowledgeNode n = new KnowledgeNode();
        n.setNodeCode(code);
        n.setTitle(title);
        n.setDifficultyLevel(difficulty);
        n.setActive(true);
        n.setPrerequisiteNode(prereq);
        return n;
    }

    private void seedExercises(KnowledgeNode addition, KnowledgeNode subtraction,
                                KnowledgeNode multiplication, KnowledgeNode division,
                                KnowledgeNode fractionsSimplify, KnowledgeNode fractionsAddSub,
                                KnowledgeNode algebraLinear, KnowledgeNode algebraFactorize) {

        List<Exercise> exercises = List.of(
            // Addition
            ex(addition, "What is 47 + 38?", "85", 1),
            ex(addition, "Calculate 123 + 456.", "579", 1),
            ex(addition, "What is 999 + 1?", "1000", 1),
            ex(addition, "Find the sum of 256 and 744.", "1000", 2),

            // Subtraction
            ex(subtraction, "What is 100 - 37?", "63", 1),
            ex(subtraction, "Calculate 500 - 289.", "211", 2),
            ex(subtraction, "What is 1000 - 456?", "544", 2),
            ex(subtraction, "Find the difference between 800 and 347.", "453", 2),

            // Multiplication
            ex(multiplication, "What is 7 × 8?", "56", 1),
            ex(multiplication, "Calculate 12 × 15.", "180", 2),
            ex(multiplication, "What is 24 × 5?", "120", 2),
            ex(multiplication, "Find the product of 13 and 11.", "143", 2),

            // Division
            ex(division, "What is 84 ÷ 7?", "12", 1),
            ex(division, "Calculate 144 ÷ 12.", "12", 2),
            ex(division, "What is 225 ÷ 15?", "15", 2),
            ex(division, "Divide 256 by 8.", "32", 2),

            // Fractions - Simplify
            ex(fractionsSimplify, "Simplify 8/12.", "2/3", 3),
            ex(fractionsSimplify, "Simplify 15/25.", "3/5", 3),
            ex(fractionsSimplify, "Simplify 18/24.", "3/4", 3),
            ex(fractionsSimplify, "Simplify 36/48.", "3/4", 3),

            // Fractions - Add/Sub
            ex(fractionsAddSub, "Calculate 1/4 + 1/4.", "1/2", 3),
            ex(fractionsAddSub, "What is 3/5 + 1/5?", "4/5", 3),
            ex(fractionsAddSub, "Calculate 2/3 - 1/6.", "1/2", 4),
            ex(fractionsAddSub, "What is 5/6 - 1/3?", "1/2", 4),

            // Algebra - Linear Equations
            ex(algebraLinear, "Solve for x: x + 5 = 12.", "7", 4),
            ex(algebraLinear, "Solve for x: 2x = 18.", "9", 4),
            ex(algebraLinear, "Solve for x: 3x - 4 = 11.", "5", 4),
            ex(algebraLinear, "Solve for x: x/4 + 2 = 6.", "16", 5),

            // Algebra - Factorization
            ex(algebraFactorize, "Factorize: x^2 - 9.", "(x-3)(x+3)", 5),
            ex(algebraFactorize, "Factorize: x^2 + 5x + 6.", "(x+2)(x+3)", 5),
            ex(algebraFactorize, "Factorize: x^2 - 7x + 12.", "(x-3)(x-4)", 5),
            ex(algebraFactorize, "Factorize: 2x^2 + 6x.", "2x(x+3)", 5)
        );

        exerciseRepository.saveAll(exercises);
    }

    private Exercise ex(KnowledgeNode node, String question, String answer, int difficulty) {
        Exercise e = new Exercise();
        e.setKnowledgeNode(node);
        e.setQuestionText(question);
        e.setCorrectAnswer(answer);
        e.setDifficultyLevel(difficulty);
        e.setActive(true);
        return e;
    }

    // ---------------- LESSONS ----------------

    private void seedLessons() {
        if (lessonRepository.count() > 0) return;

        Lesson addition = lesson("ARITH_ADDITION", 3,
                "Addition is how we combine quantities. Every time you count your money, score points in a game, or stack books, you're doing addition without thinking about it.",
                "Addition combines two or more numbers (called addends) into a single total (the sum). For multi-digit numbers, write them in a column lined up by place value — ones under ones, tens under tens, hundreds under hundreds — then add each column from right to left. When a column's total reaches 10 or more, carry the leftmost digit to the next column.",
                List.of(
                        "47 + 38\nLine up by place value:\n  47\n+ 38\nOnes column: 7 + 8 = 15. Write 5, carry 1.\nTens column: 4 + 3 + 1 (carried) = 8.\nAnswer: 85",
                        "256 + 744\nOnes: 6 + 4 = 10. Write 0, carry 1.\nTens: 5 + 4 + 1 = 10. Write 0, carry 1.\nHundreds: 2 + 7 + 1 = 10.\nAnswer: 1000"
                ),
                "The most common mistake is forgetting to carry. After you write a ones digit, immediately put a small '1' above the next column so you don't forget it on your next step."
        );

        Lesson subtraction = lesson("ARITH_SUBTRACTION", 3,
                "Subtraction is the opposite of addition. It answers the questions 'what's left after I take some away?' and 'what's the difference between these two amounts?'.",
                "Subtraction takes one number (the subtrahend) away from another (the minuend) and gives a result called the difference. For multi-digit numbers, line them up by place value and subtract column by column starting from the right. If the top digit is smaller than the bottom digit, borrow 10 from the next column to the left — that column drops by 1 and the current column gains 10.",
                List.of(
                        "100 - 37\nOnes: 0 - 7 is impossible. Borrow.\nThe tens column is 0, so chain-borrow from hundreds:\n  hundreds becomes 0, tens becomes 9, ones becomes 10.\nNow: 10 - 7 = 3, 9 - 3 = 6, 0 - 0 = 0.\nAnswer: 63",
                        "500 - 289\nOnes: 0 - 9 → borrow (chain through tens and hundreds).\nAfter borrowing: hundreds=4, tens=9, ones=10.\n10 - 9 = 1, 9 - 8 = 1, 4 - 2 = 2.\nAnswer: 211"
                ),
                "When the top number contains zeros, expect to chain-borrow across several columns. Cross out each digit as you change it so you don't lose track."
        );

        Lesson multiplication = lesson("ARITH_MULTIPLICATION", 3,
                "Multiplication is repeated addition made fast. Knowing 7 × 8 instantly means you don't have to add 7 + 7 + 7 + 7 + 7 + 7 + 7 + 7 every time you need that answer.",
                "Multiplication scales one number (the multiplicand) by another (the multiplier). 4 × 5 means 'four groups of five' — or equivalently 'five groups of four' — and the answer is 20 either way. For multi-digit problems, multiply each digit of one number by each digit of the other, then add the partial products together, shifting each row one position to the left.",
                List.of(
                        "12 × 15\nBreak it into pieces:\n  12 × 5 = 60\n  12 × 10 = 120\nAdd the pieces: 60 + 120 = 180\nAnswer: 180",
                        "13 × 11\n  13 × 1 (ones) = 13\n  13 × 1 (tens) = 130\nAdd: 13 + 130 = 143\nAnswer: 143"
                ),
                "Memorise times tables up to 12 × 12. It will save you minutes on every future problem — division, fractions, and algebra all rely on you recalling these facts instantly."
        );

        Lesson division = lesson("ARITH_DIVISION", 3,
                "Division is multiplication in reverse. If multiplication asks 'how many in total?', division asks the opposite: 'if I share this evenly into groups, how many will each group have?'.",
                "Division splits a number (the dividend) into equal groups based on another number (the divisor), giving the size of each group (the quotient). For long division, take the leftmost digits of the dividend that the divisor can divide into, write the quotient digit on top, multiply back and subtract, bring down the next digit of the dividend, then repeat.",
                List.of(
                        "144 ÷ 12\n12 goes into 14 once (1 × 12 = 12, remainder 2).\nBring down the 4 → 24.\n12 goes into 24 twice (2 × 12 = 24, remainder 0).\nAnswer: 12",
                        "256 ÷ 8\n8 into 25 = 3 (3 × 8 = 24, remainder 1).\nBring down the 6 → 16.\n8 into 16 = 2 exactly.\nAnswer: 32"
                ),
                "Always verify division by multiplying back. If 144 ÷ 12 = 12, then 12 × 12 should equal 144. If it doesn't, you made an arithmetic slip somewhere — go back and find it."
        );

        Lesson fractionsSimplify = lesson("FRACTIONS_SIMPLIFY", 4,
                "A fraction is really just a way to write division. 8/12 means '8 divided into 12 equal parts'. Simplifying gives the same value in its cleanest form — easier to read, easier to compare to other fractions.",
                "A fraction is in its simplest form when the numerator (top) and denominator (bottom) share no common factors greater than 1. To simplify, find the Greatest Common Divisor (GCD) of the two numbers — the biggest number that divides both evenly — then divide both top and bottom by it. The resulting fraction has the same value but smaller numbers.",
                List.of(
                        "Simplify 8/12\nFactors of 8: 1, 2, 4, 8\nFactors of 12: 1, 2, 3, 4, 6, 12\nGreatest common factor: 4\nDivide top and bottom by 4: 8÷4 = 2, 12÷4 = 3\nAnswer: 2/3",
                        "Simplify 18/24\nGCD(18, 24) = 6\n18 ÷ 6 = 3, 24 ÷ 6 = 4\nAnswer: 3/4"
                ),
                "If you can't spot the GCD right away, divide both numbers by small primes one at a time (2, then 3, then 5, then 7...) until nothing divides evenly anymore. You'll end up at the simplest form either way."
        );

        Lesson fractionsAddSub = lesson("FRACTIONS_ADD_SUB", 5,
                "You can only add or subtract fractions when they share the same denominator. Think of it like adding apples and oranges — you have to convert them to the same 'unit' first before the operation makes sense.",
                "If two fractions already have the same denominator, just add or subtract the numerators and keep the denominator unchanged. If the denominators are different, first find the Least Common Denominator (LCD) — the smallest number that both denominators divide into — then convert each fraction by multiplying its top AND bottom by the same number so the denominators match. Now you can add or subtract.",
                List.of(
                        "1/4 + 1/4\nSame denominator. Add the tops: 1 + 1 = 2.\nKeep the denominator: 2/4.\nSimplify: 2/4 = 1/2\nAnswer: 1/2",
                        "2/3 - 1/6\nDifferent denominators. LCD(3, 6) = 6.\nConvert 2/3 → multiply top and bottom by 2 → 4/6.\nNow subtract: 4/6 - 1/6 = 3/6.\nSimplify: 3/6 = 1/2\nAnswer: 1/2"
                ),
                "Always simplify your final answer. 4/8 and 1/2 represent the same amount, but 1/2 is the proper form and will match the expected answer in most exercises."
        );

        Lesson algebraLinear = lesson("ALGEBRA_LINEAR", 5,
                "A linear equation is a puzzle: there's a hidden number we call x, and your job is to figure out what number it must be. The whole strategy is to isolate x by itself on one side of the equals sign.",
                "An equation says two things are equal — whatever you do to one side, you must do to the other to keep the equation balanced. To solve for x, undo the operations attached to it in reverse order: if x has something added to it, subtract; if x has been multiplied, divide. Each opposite operation 'cancels out' what was done to x, peeling off one layer at a time.",
                List.of(
                        "Solve x + 5 = 12\nTo isolate x, undo the +5 by subtracting 5 from BOTH sides:\n  x + 5 - 5 = 12 - 5\n  x = 7\nAnswer: x = 7",
                        "Solve 3x - 4 = 11\nFirst undo the -4 by adding 4 to both sides:\n  3x = 15\nThen undo the ×3 by dividing both sides by 3:\n  x = 5\nAnswer: x = 5"
                ),
                "Always undo addition and subtraction BEFORE multiplication and division. Think of x as wrapped in layers — peel the outer layers (the +/-) off first, then deal with the inner layers (the ×/÷)."
        );

        Lesson algebraFactorize = lesson("ALGEBRA_FACTORIZE", 6,
                "Factorisation is the reverse of expanding. If multiplying (x + 2)(x + 3) gives x² + 5x + 6, then factorising x² + 5x + 6 should give back (x + 2)(x + 3). You're 'un-multiplying' an expression.",
                "To factor a quadratic like x² + bx + c, find two numbers that multiply to give c and add to give b. Those two numbers go into (x + ?)(x + ?). For difference of squares like x² - 9, recognise it factors directly as (x - 3)(x + 3) — the pattern a² - b² = (a-b)(a+b). For expressions with a common factor like 2x² + 6x, always pull out the largest common factor first: 2x(x + 3).",
                List.of(
                        "Factor x² + 5x + 6\nNeed two numbers that multiply to 6 and add to 5.\nTry 2 and 3: 2 × 3 = 6 ✓, 2 + 3 = 5 ✓\nAnswer: (x + 2)(x + 3)",
                        "Factor x² - 9\nThis is a difference of squares: 9 = 3².\nPattern: a² - b² = (a - b)(a + b)\nAnswer: (x - 3)(x + 3)"
                ),
                "Always check your answer by expanding it back out (FOIL: First, Outer, Inner, Last). If (x + 2)(x + 3) expands to x² + 5x + 6, your factorisation is correct."
        );

        lessonRepository.saveAll(List.of(
                addition, subtraction, multiplication, division,
                fractionsSimplify, fractionsAddSub, algebraLinear, algebraFactorize
        ));
    }

    private Lesson lesson(String nodeCode, int estimatedMinutes,
                          String intro, String theory, List<String> examples, String tip) {
        KnowledgeNode node = knowledgeNodeRepository.findByNodeCode(nodeCode)
                .orElseThrow(() -> new IllegalStateException("Knowledge node not found: " + nodeCode));
        Lesson l = new Lesson();
        l.setKnowledgeNode(node);
        l.setEstimatedMinutes(estimatedMinutes);
        l.setIntro(intro);
        l.setTheory(theory);
        l.setExamples(examples);
        l.setTip(tip);
        l.setActive(true);
        return l;
    }
}
