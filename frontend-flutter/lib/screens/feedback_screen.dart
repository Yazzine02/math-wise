import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../models/ai_feedback.dart';

class FeedbackScreen extends StatelessWidget {
  final AiFeedback feedback;

  const FeedbackScreen({super.key, required this.feedback});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Result')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _ResultBanner(isCorrect: feedback.isCorrect),
            const SizedBox(height: 32),
            if (!feedback.isCorrect) ...[
              if (feedback.weaknessNode.isNotEmpty) ...[
                Text('Identified weakness', style: Theme.of(context).textTheme.labelLarge?.copyWith(color: Colors.grey)),
                const SizedBox(height: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    feedback.weaknessNode,
                    style: TextStyle(color: Theme.of(context).colorScheme.onPrimaryContainer, fontWeight: FontWeight.bold),
                  ),
                ),
                const SizedBox(height: 24),
              ],
              if (feedback.explanation.isNotEmpty) ...[
                Text("What went wrong", style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text(feedback.explanation, style: Theme.of(context).textTheme.bodyLarge),
                const SizedBox(height: 32),
              ],
            ],
            const Spacer(),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () => context.pop(),
                child: const Text('Next Exercise'),
              ),
            ),
            const SizedBox(height: 12),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: () => context.go('/home'),
                child: const Text('Back to Dashboard'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ResultBanner extends StatelessWidget {
  final bool isCorrect;
  const _ResultBanner({required this.isCorrect});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: isCorrect ? Colors.green.shade50 : Colors.red.shade50,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: isCorrect ? Colors.green : Colors.red, width: 2),
      ),
      child: Row(
        children: [
          Icon(isCorrect ? Icons.check_circle : Icons.cancel, color: isCorrect ? Colors.green : Colors.red, size: 36),
          const SizedBox(width: 16),
          Text(
            isCorrect ? 'Correct!' : 'Not quite right',
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
              color: isCorrect ? Colors.green.shade800 : Colors.red.shade800,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
