
Tested and working:
1. Normal Auto-Grader flow in FE (using 18 test cases)
2. Using AI Generated Test cases in the auto-grader flow. (note that 5 max tc generated per qn - based on system prompt)
3. Using no exisitng test cases (full AI generated testers -> autograder)  
4. CLI flow one shot works fine, can view run details in FE also.  
5. Same with manual CLI flow (theres a minor bug where i cant see the char i type to exit, but is a small thing...)


for FE, can mention usage of react + vite (typescript) , with shadcn & wtv css package it uses

then also important is about the heyapi integration for FE types generation + generated SDK hooks, to avoid manual wiring and integration with the backend


Tested and working:
1. Normal Auto-Grader flow in FE (using 18 test cases)
2. Using AI Generated Test cases in the auto-grader flow. (note that 5 max tc generated per qn - based on system prompt)
3. Using no exisitng test cases (full AI generated testers -> autograder)  
4. CLI flow one shot works fine, can view run details in FE also.  
5. Same with manual CLI flow (theres a minor bug where i cant see the char i type to exit, but is a small thing...)


my raw notes on the flow:
plain CLI flow, 
• show the params to pass in,and then the output

one shot CLI
• show how one shot command can do the process if we prefill all the params.

frontend UI flow
• show the uploading of the files, walk thru that we just replicate the cli flow with a frontend, and the tools are more useful -> insights of the report's data, pdf viewer, jplag viewer, downloading all as one zip, as well as the web "IDE" style viewer, allowing to view the student's submissions + the test cases at the bottom, leetcode style.

frontend with test case generated 
• use the AI generation flow
• introduce that docling is an added service in our docker compose, that helps parse the pdf files along with images. 
• upload the sample questions, + tester files 
• show how it infers the question structure from both the folders & the pdf (we must be damn clear abt the logic here)
• then from there, it will pass to two stages, creating recommendations of what test cases to add, as well as then generating based on those recommendations. mention it has a robust retry mechanism & what the inputs for the AI generation is.  (System prompt + data from the question - can add more detials of what it passes) 
• go to the final page and export the generated test cases, 
• now back to the grader tab, we upload the student files, and then the new generated testers. run thru the eval and show how the marks are now different since the test cases are added. 
• view the results
• we can also show jplag if got time (perhaps can do earlier , but we see time constraint)

// we have not shown how it can also generate from scratch at this point. perhaps we can see if got sufficinet time to do that 
important that we showcase the output folder, with the pdf report, csv scores, as well as the jplag viewer.

• this is a full flow of just demo-ing the application. 

we also need to walk thru the packages, repo design & app strcuture

can also mention lanterna for the CLI terminal bar. should also mention FE uses shadcn compoentns, with some cool ones taken from shadcn ui

for FE, can mention usage of react + vite (typescript) , with shadcn & wtv css package it uses

then also important is about the heyapi integration for FE types generation + generated SDK hooks, to avoid manual wiring and integration with the backend