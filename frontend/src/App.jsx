import React, { useEffect, useRef, useState } from 'react';
import gsap from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { MousePointer2, Check, ExternalLink, Code2, Play, Users, FolderArchive, FileCode2, FileSpreadsheet, Activity, AlertTriangle } from 'lucide-react';

gsap.registerPlugin(ScrollTrigger);

const DiagnosticShuffler = () => {
  const [cards, setCards] = useState([
    { id: 1, label: 'Extract ZIP Archives', sub: 'Depth-first recursive unzip' },
    { id: 2, label: 'Identity Resolution', sub: 'Header regex pattern match' },
    { id: 3, label: 'Structure Normalizer', sub: 'Directory tree unification' }
  ]);

  useEffect(() => {
    const interval = setInterval(() => {
      setCards(prev => {
        const newCards = [...prev];
        const last = newCards.pop();
        newCards.unshift(last);
        return newCards;
      });
    }, 3000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="relative w-full h-[300px] flex items-center justify-center">
      {cards.map((card, i) => (
        <div
          key={card.id}
          className="absolute w-full max-w-[280px] p-6 rounded-[2rem] border border-primary/10 shadow-xl transition-all duration-[800ms] flex flex-col justify-center items-center text-center bg-background"
          style={{
            transform: `translateY(${i * 20}px) scale(${1 - i * 0.05})`,
            zIndex: 10 - i,
            opacity: 1 - i * 0.2,
            transitionTimingFunction: 'cubic-bezier(0.34, 1.56, 0.64, 1)'
          }}
        >
          <div className="w-10 h-10 rounded-full bg-accent/10 flex items-center justify-center mb-4 text-accent">
            <Check size={20} />
          </div>
          <h4 className="font-sans font-bold text-charcoal mb-1">{card.label}</h4>
          <p className="font-data text-xs text-charcoal/60 uppercase tracking-widest">{card.sub}</p>
        </div>
      ))}
    </div>
  );
};

const TelemetryTypewriter = () => {
  const [text, setText] = useState('');
  const fullText = "> Starting secure execution env...\n> Compiling student source code...\n> OK: 0 syntax errors.\n> Running tester payload...\n> Output captured.\n> SECURE TIMEOUT: false\n> Score recorded: 100/100.";
  
  useEffect(() => {
    let index = 0;
    const interval = setInterval(() => {
      setText(fullText.substring(0, index));
      index++;
      if (index > fullText.length) {
        setTimeout(() => { index = 0; setText(''); }, 2000);
      }
    }, 50);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="w-full h-[300px] bg-charcoal rounded-[2rem] p-6 relative overflow-hidden flex flex-col">
      <div className="flex items-center gap-2 mb-4 mb-auto">
        <div className="w-2 h-2 rounded-full bg-accent animate-pulse"></div>
        <span className="font-data text-xs text-background/60 uppercase tracking-widest">Live Feed</span>
      </div>
      <div className="font-data text-sm text-background/90 whitespace-pre-line leading-relaxed pb-4">
        {text}<span className="inline-block w-2 h-4 bg-accent animate-pulse ml-1 align-middle"></span>
      </div>
    </div>
  );
};

const CursorProtocolScheduler = () => {
  const containerRef = useRef();
  
  useEffect(() => {
    const ctx = gsap.context(() => {
      const tl = gsap.timeline({ repeat: -1, repeatDelay: 1 });
      
      // Reset
      gsap.set('.sim-cursor', { x: 0, y: 150, opacity: 0 });
      gsap.set('.day-cell', { scale: 1, backgroundColor: 'transparent' });
      gsap.set('.save-btn', { scale: 1, backgroundColor: '#F2F0E9' });
      
      tl.to('.sim-cursor', { opacity: 1, duration: 0.2 })
        .to('.sim-cursor', { x: 120, y: 60, duration: 0.8, ease: 'power2.inOut' })
        // click cell
        .to('.sim-cursor', { scale: 0.9, duration: 0.1 })
        .to('.day-target', { scale: 0.95, duration: 0.1 }, '<')
        .to('.day-target', { backgroundColor: '#CC5833', color: '#fff', scale: 1, duration: 0.2 })
        .to('.sim-cursor', { scale: 1, duration: 0.1 }, '<')
        // move to save
        .to('.sim-cursor', { x: 200, y: 220, duration: 0.8, ease: 'power2.inOut', delay: 0.2 })
        .to('.sim-cursor', { scale: 0.9, duration: 0.1 })
        .to('.save-btn', { scale: 0.95, backgroundColor: '#2E4036', color: '#fff', duration: 0.1 }, '<')
        .to('.sim-cursor', { scale: 1, duration: 0.1 })
        .to('.save-btn', { scale: 1, duration: 0.2 }, '<')
        .to('.sim-cursor', { opacity: 0, duration: 0.2, delay: 0.5 });
        
    }, containerRef);
    return () => ctx.revert();
  }, []);

  const days = ['S', 'M', 'T', 'W', 'T', 'F', 'S'];

  return (
    <div ref={containerRef} className="w-full h-[300px] flex flex-col items-center justify-center relative bg-background border border-primary/10 rounded-[2rem] p-6 shadow-sm">
      <div className="w-full max-w-[250px]">
        <h4 className="font-sans font-bold text-charcoal mb-4">Export Pipeline</h4>
        <div className="grid grid-cols-7 gap-2 mb-8">
          {days.map((d, i) => (
            <div key={i} className={`day-cell aspect-square rounded-lg border border-primary/20 flex items-center justify-center font-data text-xs ${i === 3 ? 'day-target' : 'text-charcoal/50'}`}>
              {d}
            </div>
          ))}
        </div>
        
        <div className="flex justify-end w-full">
          <div className="save-btn px-4 py-2 border border-primary/20 rounded-full font-sans text-sm font-semibold text-charcoal">
            Export .CSV
          </div>
        </div>
      </div>
      
      <div className="sim-cursor absolute z-10 pointer-events-none text-charcoal">
        <MousePointer2 fill="#1A1A1A" size={24} />
      </div>
    </div>
  );
};

export default function App() {
  const [isScrolled, setIsScrolled] = useState(false);
  const mainRef = useRef();

  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  
  // File selection state for visibility of system status
  const [fileCounts, setFileCounts] = useState({ submissions: 0, testers: 0, scoresheet: 0 });

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 50);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    const ctx = gsap.context(() => {
      // Hero Animation
      gsap.fromTo('.hero-text', 
        { y: 40, opacity: 0 }, 
        { y: 0, opacity: 1, duration: 1, stagger: 0.08, ease: 'power3.out', delay: 0.2 }
      );

      // Philosophy Animation
      gsap.fromTo('.philosophy-word',
        { y: 30, opacity: 0 },
        {
          y: 0, opacity: 1, stagger: 0.05, duration: 0.8, ease: 'power3.out',
          scrollTrigger: { trigger: '.philosophy-section', start: 'top 70%' }
        }
      );

      // Protocol Stacking ScrollTrigger
      const cards = gsap.utils.toArray('.protocol-card');
      
      ScrollTrigger.create({
        trigger: '.protocol-section',
        start: 'top top',
        end: `+=${window.innerHeight * cards.length}`,
        pin: true,
        scrub: true,
        animation: gsap.timeline()
          // Card 1 to Card 2
          .to('.protocol-card-1', { scale: 0.9, filter: 'blur(20px)', opacity: 0.5, ease: 'none' }, 0)
          .fromTo('.protocol-card-2', { y: '100%' }, { y: '0%', ease: 'none' }, 0)
          // Card 2 to Card 3
          .to('.protocol-card-2', { scale: 0.9, filter: 'blur(20px)', opacity: 0.5, ease: 'none' }, 1)
          .fromTo('.protocol-card-3', { y: '100%' }, { y: '0%', ease: 'none' }, 1)
      });
      
      // Step Anim 1: Rotating motif
      gsap.to('.motif-rotate', { rotation: 360, duration: 20, repeat: -1, ease: 'none' });
      
      // Step Anim 2: Laser
      gsap.fromTo('.laser-line', { y: 0 }, { y: 200, duration: 2, repeat: -1, yoyo: true, ease: 'power2.inOut' });

    }, mainRef);
    return () => ctx.revert();
  }, []);

  const handleGradeSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResult(null);

    const formElement = e.target;
    const body = new FormData(formElement);

    try {
      const response = await fetch('http://localhost:8080/api/grade', {
        method: 'POST',
        body: body,
      });
      const data = await response.json();
      setResult(data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div ref={mainRef} className="relative w-full min-h-screen selection:bg-accent/30 selection:text-charcoal">
      <div className="noise-overlay" />

      {/* NAVBAR */}
      <nav className={`fixed top-6 left-1/2 -translate-x-1/2 z-50 transition-all duration-500 rounded-full px-6 py-3 flex items-center justify-between gap-12 ${
        isScrolled ? 'bg-background/80 backdrop-blur-xl border border-primary/20 shadow-lg w-[90%] max-w-4xl' : 'bg-transparent w-full px-12'
      }`}>
        <div className={`font-outfit font-bold tracking-tight text-xl ${isScrolled ? 'text-primary' : 'text-background'}`}>
          IS442 Auto-Grader
        </div>
        <div className="hidden md:flex items-center gap-8">
          <a href="#features" className={`font-sans text-sm font-medium transition-colors hover:text-accent ${isScrolled ? 'text-primary/70' : 'text-background/70'}`}>Platform</a>
          <a href="#protocol" className={`font-sans text-sm font-medium transition-colors hover:text-accent ${isScrolled ? 'text-primary/70' : 'text-background/70'}`}>Protocol</a>
          <button className="btn-magnetic bg-accent text-background px-6 py-2.5 font-sans font-semibold text-sm rounded-full flex items-center gap-2"
                  onClick={() => document.getElementById('get-started').scrollIntoView({behavior: 'smooth'})}>
            Choose Folders
          </button>
        </div>
      </nav>

      {/* HERO SECTION */}
      <section className="relative h-[100dvh] w-full flex items-end pb-24 px-8 md:px-24">
        {/* Background Image with Gradient Overlay */}
        <div className="absolute inset-0 z-0">
          <img 
            src="https://images.unsplash.com/photo-1524178232363-1fb2b075b655?q=80&w=2000&auto=format&fit=crop" 
            alt="University professor teaching students in a modern lecture hall" 
            className="w-full h-full object-cover opacity-70"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-charcoal via-charcoal/80 to-primary/40" />
        </div>

        <div className="relative z-10 max-w-4xl">
          <div className="flex flex-col gap-2">
            <h1 className="hero-text text-background text-5xl md:text-7xl lg:text-[6rem] font-sans font-extrabold leading-[0.9] tracking-tighter">
              Automated
            </h1>
            <h1 className="hero-text text-accent text-6xl md:text-8xl lg:text-[8rem] font-drama italic font-medium leading-[0.8]">
              Evaluation.
            </h1>
          </div>
          <p className="hero-text text-background/90 font-sans text-lg md:text-xl mt-8 max-w-xl font-light">
            Designed for teaching assistants and professors. Standardize submissions, secure code execution, and generate LMS-ready reports instantly.
          </p>
          <div className="hero-text mt-10">
            <button className="btn-magnetic bg-accent text-background px-10 py-5 font-sans font-bold text-lg rounded-full shadow-[0_10px_40px_rgba(204,88,51,0.4)]"
                    onClick={() => document.getElementById('get-started').scrollIntoView({behavior: 'smooth'})}>
              Grade Assignments
            </button>
          </div>
        </div>
      </section>

      {/* FEATURES SECTION */}
      <section id="features" className="py-32 px-8 md:px-24 bg-background">
        <div className="max-w-7xl mx-auto">
          <div className="mb-16">
            <h2 className="font-sans text-sm font-bold tracking-widest uppercase text-accent mb-2">Platform Capabilities</h2>
            <h3 className="font-outfit text-4xl md:text-5xl font-bold text-primary max-w-2xl leading-tight">
              Consistent, fair, and fast grading for large university cohorts.
            </h3>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="flex flex-col gap-6">
              <DiagnosticShuffler />
              <div>
                <h4 className="font-outfit text-2xl font-bold text-charcoal mb-2">Submission Normalization</h4>
                <p className="font-sans text-charcoal/70">Automatically resolves student identities and unifies varied folder structures into a standardized format.</p>
              </div>
            </div>

            {/* Feature 2 */}
            <div className="flex flex-col gap-6">
              <TelemetryTypewriter />
              <div>
                <h4 className="font-outfit text-2xl font-bold text-charcoal mb-2">Secure Compilation Env</h4>
                <p className="font-sans text-charcoal/70">Compiles and executes code against test suites in an isolated sandbox with uncompromising timeout protection.</p>
              </div>
            </div>

            {/* Feature 3 */}
            <div className="flex flex-col gap-6">
              <CursorProtocolScheduler />
              <div>
                <h4 className="font-outfit text-2xl font-bold text-charcoal mb-2">Algorithmic Reporting</h4>
                <p className="font-sans text-charcoal/70">Assembles results into rich LMS-ready CSVs and pristine PDF artifacts highlighting all granular structural anomalies.</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* PHILOSOPHY SECTION */}
      <section className="philosophy-section relative py-40 px-8 md:px-24 bg-charcoal flex items-center justify-center overflow-hidden">
        <div className="absolute inset-0 opacity-10">
          <img 
            src="https://images.unsplash.com/photo-1618605553075-8120468352ce?q=80&w=2000&auto=format&fit=crop" 
            alt="Lab Glassware Texture" 
            className="w-full h-full object-cover grayscale"
          />
        </div>
        <div className="relative z-10 max-w-5xl mx-auto text-center flex flex-col gap-8">
          <h3 className="font-sans text-xl md:text-3xl text-background/50 font-medium tracking-tight">
            {"Tired of manually validating folder names and unzipping files?".split(' ').map((w, i) => (
              <span key={i} className="philosophy-word inline-block mr-2">{w}</span>
            ))}
          </h3>
          <h2 className="font-drama italic text-5xl md:text-7xl text-background font-medium leading-tight">
            {"Let the system ".split(' ').map((w, i) => (
              <span key={i} className="philosophy-word inline-block mr-3">{w}</span>
            ))}
            <span className="philosophy-word inline-block text-accent">automate</span>
            {" the busywork.".split(' ').map((w, i) => (
              <span key={i} className="philosophy-word inline-block ml-3">{w}</span>
            ))}
          </h2>
        </div>
      </section>

      {/* PROTOCOL SECTION */}
      <section id="protocol" className="protocol-section h-screen relative bg-background overflow-hidden">
        {/* Card 1 */}
        <div className="protocol-card protocol-card-1 absolute inset-0 w-full h-full flex flex-col md:flex-row items-center p-8 md:p-24 pb-32">
          <div className="flex-1">
            <span className="font-data text-accent text-2xl mb-4 block">01</span>
            <h2 className="font-outfit text-6xl font-bold text-primary mb-6">Archive Ingestion</h2>
            <p className="font-sans text-2xl text-charcoal/70 max-w-lg">
              Depth-first traversal of organic submission archives. Eradicates malformed nested ZIP structures automatically.
            </p>
          </div>
          <div className="flex-1 flex justify-center items-center h-full pt-24 md:pt-0">
            <svg className="motif-rotate w-64 h-64 text-primary opacity-20" viewBox="0 0 100 100" fill="none" stroke="currentColor" strokeWidth="1">
              <circle cx="50" cy="50" r="40" />
              <path d="M50 10 L50 90 M10 50 L90 50 M21.7 21.7 L78.3 78.3 M21.7 78.3 L78.3 21.7" />
              <circle cx="50" cy="50" r="20" />
            </svg>
          </div>
        </div>

        {/* Card 2 */}
        <div className="protocol-card protocol-card-2 absolute inset-0 w-full h-full bg-primary flex flex-col md:flex-row items-center p-8 md:p-24 pb-32 shadow-[0_-20px_50px_rgba(0,0,0,0.2)]">
          <div className="flex-1">
            <span className="font-data text-accent text-2xl mb-4 block">02</span>
            <h2 className="font-outfit text-6xl font-bold text-background mb-6">Compile & Run</h2>
            <p className="font-sans text-2xl text-background/70 max-w-lg">
              Strict isolation layer. Each module is compiled and forcibly terminated if it exceeds computational quotas.
            </p>
          </div>
          <div className="flex-1 flex justify-center items-center h-full pt-24 md:pt-0">
            <div className="relative w-64 h-64 border border-background/20 rounded-3xl overflow-hidden bg-charcoal/50">
              <div className="grid grid-cols-4 grid-rows-4 gap-2 w-full h-full p-4">
                {Array.from({length: 16}).map((_, i) => <div key={i} className="bg-background/10 rounded-lg"></div>)}
              </div>
              <div className="laser-line absolute top-0 left-0 w-full h-1 bg-accent shadow-[0_0_20px_rgba(204,88,51,1)]"></div>
            </div>
          </div>
        </div>

        {/* Card 3 */}
        <div className="protocol-card protocol-card-3 absolute inset-0 w-full h-full bg-charcoal flex flex-col md:flex-row items-center p-8 md:p-24 pb-32 shadow-[0_-20px_50px_rgba(0,0,0,0.5)]">
          <div className="flex-1">
            <span className="font-data text-accent text-2xl mb-4 block">03</span>
            <h2 className="font-outfit text-6xl font-bold text-background mb-6">Analytic Output</h2>
            <p className="font-sans text-2xl text-background/70 max-w-lg">
              Generation of immutable PDF dossiers and LMS-compatible CSVs. The grading loop is forever closed.
            </p>
          </div>
          <div className="flex-1 flex justify-center items-center h-full pt-24 md:pt-0">
            <svg className="w-80 h-32 text-accent" viewBox="0 0 200 50" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeDasharray="400" strokeDashoffset="0" className="animate-[dash_3s_linear_infinite]" 
                    d="M 0 25 L 40 25 L 50 10 L 60 45 L 70 5 L 80 35 L 90 25 L 200 25" />
            </svg>
            <style>{`@keyframes dash { to { stroke-dashoffset: -400; } }`}</style>
          </div>
        </div>
      </section>

      {/* GET STARTED / FORM SECTION */}
      <section id="get-started" className="py-32 px-8 md:px-24 bg-background border-t border-primary/10">
        <div className="max-w-4xl mx-auto glass-card p-8 md:p-12 relative overflow-hidden">
          <div className="absolute top-0 left-0 w-2 h-full bg-accent"></div>
          <h2 className="font-outfit text-4xl font-bold text-primary mb-2">Grade Assignments</h2>
          <p className="font-sans text-charcoal/70 mb-10">Upload your folders and scoresheet to automatically compile, execute, and grade student submissions.</p>
          
          <form className="flex flex-col gap-8" onSubmit={handleGradeSubmit}>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              
              <div className="flex flex-col gap-2 relative group">
                <label className="font-data text-xs uppercase tracking-widest text-primary font-semibold">1. Submissions Directory *</label>
                <div className="text-xs text-charcoal/50 mb-3">Folder containing student .zip archives.</div>
                <div className="relative border-2 border-dashed border-primary/20 rounded-2xl p-8 flex flex-col items-center justify-center text-center transition-all duration-300 bg-background hover:bg-primary/5 hover:border-accent group-hover:shadow-[0_4px_20px_rgba(0,0,0,0.05)] w-full h-40">
                  <input required type="file" name="submissions" multiple webkitdirectory="true" directory="true" 
                    onChange={(e) => setFileCounts(p => ({...p, submissions: e.target.files.length}))}
                    className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" />
                  <FolderArchive size={32} className={`mb-3 transition-colors ${fileCounts.submissions > 0 ? 'text-accent' : 'text-primary/40'}`} />
                  {fileCounts.submissions > 0 ? (
                    <span className="font-sans font-bold text-accent px-4 py-1.5 bg-accent/10 rounded-full text-sm">
                      {fileCounts.submissions} files secured
                    </span>
                  ) : (
                    <>
                      <span className="font-sans font-bold text-primary mb-1">Click to Browse</span>
                      <span className="font-data text-[10px] uppercase tracking-widest text-primary/40">or Drag &amp; Drop</span>
                    </>
                  )}
                </div>
              </div>

              <div className="flex flex-col gap-2 relative group">
                <label className="font-data text-xs uppercase tracking-widest text-primary font-semibold">2. Testers Directory *</label>
                <div className="text-xs text-charcoal/50 mb-3">Folder containing JUnit .java files.</div>
                <div className="relative border-2 border-dashed border-primary/20 rounded-2xl p-8 flex flex-col items-center justify-center text-center transition-all duration-300 bg-background hover:bg-primary/5 hover:border-accent group-hover:shadow-[0_4px_20px_rgba(0,0,0,0.05)] w-full h-40">
                  <input required type="file" name="testers" multiple webkitdirectory="true" directory="true" 
                    onChange={(e) => setFileCounts(p => ({...p, testers: e.target.files.length}))}
                    className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" />
                  <FileCode2 size={32} className={`mb-3 transition-colors ${fileCounts.testers > 0 ? 'text-accent' : 'text-primary/40'}`} />
                  {fileCounts.testers > 0 ? (
                    <span className="font-sans font-bold text-accent px-4 py-1.5 bg-accent/10 rounded-full text-sm">
                      {fileCounts.testers} files secured
                    </span>
                  ) : (
                    <>
                      <span className="font-sans font-bold text-primary mb-1">Click to Browse</span>
                      <span className="font-data text-[10px] uppercase tracking-widest text-primary/40">or Drag &amp; Drop</span>
                    </>
                  )}
                </div>
              </div>

            </div>
            
            <div className="pt-2">
              <div className="flex flex-col gap-2 relative group max-w-md mx-auto">
                <label className="font-data text-xs uppercase tracking-widest text-primary font-semibold text-center">3. Scoresheet Template (Optional)</label>
                <div className="text-xs text-charcoal/50 mb-3 text-center">Upload LMS CSV template to map grades.</div>
                <div className="relative border-2 border-dashed border-primary/20 rounded-2xl p-6 flex flex-col items-center justify-center text-center transition-all duration-300 bg-background hover:bg-primary/5 hover:border-accent group-hover:shadow-[0_4px_20px_rgba(0,0,0,0.05)] w-full h-28">
                  <input type="file" name="scoresheet" accept=".csv" 
                    onChange={(e) => setFileCounts(p => ({...p, scoresheet: e.target.files.length}))}
                    className="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-10" />
                  <FileSpreadsheet size={24} className={`mb-2 transition-colors ${fileCounts.scoresheet > 0 ? 'text-accent' : 'text-primary/40'}`} />
                  {fileCounts.scoresheet > 0 ? (
                    <span className="font-sans font-bold text-accent px-3 py-1 bg-accent/10 rounded-full text-xs">
                      Scoresheet Attached
                    </span>
                  ) : (
                    <span className="font-sans font-semibold text-primary/60 text-sm">Browse for .csv</span>
                  )}
                </div>
              </div>
            </div>
            
            <button type="submit" disabled={loading} className="btn-magnetic bg-charcoal text-background py-4 mt-4 font-sans font-bold flex justify-center items-center gap-3">
              {loading ? <span className="w-5 h-5 border-2 border-background/20 border-t-background rounded-full animate-spin"></span> : <><Play size={18} fill="#F2F0E9" /> Start Grading Process</>}
            </button>
          </form>

          {result && (
            <div className="mt-16 pt-16 border-t border-primary/10 animate-[slideUp_0.5s_ease-out]">
              <div className="flex flex-col md:flex-row justify-between items-start md:items-end mb-8 gap-4">
                <div>
                  <h3 className="font-outfit text-4xl font-bold text-primary mb-2">Evaluation Dashboard</h3>
                  <span className="font-sans text-charcoal/60 font-medium">Processing complete. Results are fully generated.</span>
                </div>
                <div className="flex gap-4">
                  <div className="px-5 py-3 rounded-2xl bg-charcoal text-background flex flex-col justify-center items-center font-sans shadow-lg">
                    <span className="text-xs uppercase tracking-widest text-background/60 mb-1 font-data">Processed</span>
                    <span className="text-2xl font-bold">{result.submissions?.length || 0}</span>
                  </div>
                  <div className="px-5 py-3 rounded-2xl border border-accent/20 bg-accent/5 text-charcoal flex flex-col justify-center items-center font-sans shadow-md">
                    <span className="text-xs uppercase tracking-widest text-charcoal/50 mb-1 font-data flex items-center gap-1"><AlertTriangle size={12}/> Anomalies</span>
                    <span className="text-2xl font-bold text-accent">
                      {result.submissions?.reduce((acc, s) => acc + (s.anomalies?.length || 0), 0) || 0}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex flex-col gap-4">
                {result.submissions?.map((s, i) => (
                  <div key={i} className="flex flex-col md:flex-row items-start md:items-center justify-between p-6 rounded-2xl border border-primary/10 bg-white/50 backdrop-blur-sm hover:border-primary/30 transition-all hover:shadow-[0_8px_30px_rgba(0,0,0,0.04)] gap-4">
                    <div className="flex items-center gap-4 w-full md:w-1/3">
                      <div className="w-12 h-12 rounded-full bg-primary/5 flex items-center justify-center text-primary shrink-0">
                        <Users size={20} />
                      </div>
                      <div className="flex flex-col truncate">
                        <span className="font-outfit font-bold text-lg text-primary truncate max-w-full">{s.name || 'Unknown User'}</span>
                        <span className="font-data text-xs text-charcoal/50 truncate max-w-full">{s.username || s.displayName}</span>
                      </div>
                    </div>
                    
                    <div className="flex-1 w-full flex flex-col items-start md:items-center justify-center">
                      <div className="flex flex-wrap gap-2 w-full">
                        {s.anomalies?.length > 0 ? (
                          s.anomalies.map((a, j) => (
                            <span key={j} className="flex items-center gap-1.5 px-3 py-1.5 bg-red-50 text-red-700 text-[10px] uppercase tracking-wider rounded-lg border border-red-100 font-bold" title={a.description}>
                              <AlertTriangle size={12} /> {a.severity}
                            </span>
                          ))
                        ) : (
                          <span className="flex items-center gap-1.5 px-3 py-1.5 bg-green-50 text-green-700 text-[10px] uppercase tracking-wider rounded-lg border border-green-100 font-bold">
                            <Activity size={12} /> Nominal Build
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex flex-col items-end w-full md:w-auto mt-2 md:mt-0 pt-4 md:pt-0 border-t md:border-t-0 border-primary/10">
                      <span className="font-data text-xs uppercase tracking-widest text-charcoal/50 mb-1">Final Score</span>
                      <div className="flex items-baseline gap-1">
                        <span className={`font-outfit text-3xl font-bold ${s.totalScore === s.maxPossibleScore ? 'text-green-600' : 'text-accent'}`}>{s.totalScore}</span>
                        <span className="font-sans text-sm font-bold text-charcoal/30">/ {s.maxPossibleScore}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* FOOTER */}
      <footer className="bg-charcoal text-background rounded-t-[4rem] px-8 md:px-24 py-16 relative z-10">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-start gap-12">
          <div>
            <div className="font-outfit font-bold text-2xl mb-2">IS442 Studio</div>
            <p className="font-sans text-background/50 max-w-xs">Automated academic evaluation pipeline engineered for rapid, fair, and secure assignment processing.</p>
          </div>
          <div className="flex gap-16">
            <div className="flex flex-col gap-4">
              <h5 className="font-data text-xs uppercase tracking-widest text-background/40">Navigation</h5>
              <a href="#" className="font-sans text-sm hover:text-accent transition-colors">Platform</a>
              <a href="#" className="font-sans text-sm hover:text-accent transition-colors">Protocol</a>
              <a href="#" className="font-sans text-sm hover:text-accent transition-colors">Deploy</a>
            </div>
            <div className="flex flex-col gap-4">
              <h5 className="font-data text-xs uppercase tracking-widest text-background/40">Status</h5>
              <div className="flex items-center gap-2 border border-background/20 rounded-full px-4 py-2 bg-background/5">
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                <span className="font-data text-xs tracking-widest">SYSTEM OPERATIONAL</span>
              </div>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
